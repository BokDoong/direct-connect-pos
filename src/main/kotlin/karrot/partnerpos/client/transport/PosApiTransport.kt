package karrot.partnerpos.client.transport

import karrot.partnerpos.domain.pos.model.PosCommunicationException
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 아웃바운드 호출 대상 — 코드가 아닌 유일한 것(환경 데이터·시크릿)의 런타임 표현. */
data class PosEndpoint(val baseUrl: String, val authKey: String)

/**
 * AS-IS의 클라이언트 컴포넌트 재시도(@Retryable 4회, 5xx·타임아웃만, 지수백오프)를
 * 명시적 재시도 부품으로 재현한 것. sleeper를 주입받아 테스트에서 대기 없이 검증한다.
 */
data class RetrySpec(
    val maxAttempts: Int = 4,
    val initialBackoff: Duration = 200.milliseconds,
    val multiplier: Double = 2.0,
    val sleeper: (Duration) -> Unit = { Thread.sleep(it.inWholeMilliseconds) },
) {
    fun backoffAfter(attempt: Int) = sleeper(initialBackoff * multiplier.pow(attempt))
}

/**
 * 변하지 않는 전송 규약 1벌 — 당근 규격서가 전 파트너에 공통으로 요구하는 것만 담는다:
 * Bearer 인증, HTTP status만으로 성공/실패 판정, 5xx·타임아웃 한정 재시도 4회.
 *
 * 재시도가 안전한 근거는 order_code 멱등 처리를 규격서로 파트너에 요구했기 때문이다.
 * 무엇을 보내는지(페이로드의 의미)는 모른다 — 그것은 파트너 구현체의 소유다.
 */
class PosApiTransport(
    private val restClient: RestClient,
    private val retry: RetrySpec = RetrySpec(),
) {

    /** 응답 body를 쓰지 않는 호출 (주문 등록/취소, 매장 등록/해지 — AS-IS body 미활용 반영). */
    fun post(endpoint: PosEndpoint, path: String, body: Any) {
        exchange(endpoint, path, body) { it.toBodilessEntity() }
    }

    /** 응답 body가 계약에 포함된 호출 (재고 조회). */
    fun <T : Any> postForBody(
        endpoint: PosEndpoint,
        path: String,
        body: Any,
        responseType: ParameterizedTypeReference<T>,
    ): T = exchange(endpoint, path, body) {
        it.body(responseType) ?: throw PosCommunicationException("empty response body: $path")
    }

    /** reified 오버로드 — 제네릭 타입(List 등)이 소거되지 않게 호출 지점에서 타입 토큰을 생성한다. */
    inline fun <reified T : Any> postForBody(endpoint: PosEndpoint, path: String, body: Any): T =
        postForBody(endpoint, path, body, object : ParameterizedTypeReference<T>() {})

    private fun <T> exchange(
        endpoint: PosEndpoint,
        path: String,
        body: Any,
        extract: (RestClient.ResponseSpec) -> T,
    ): T {
        var lastError: Exception? = null
        repeat(retry.maxAttempts) { attempt ->
            try {
                val responseSpec = restClient.post()
                    .uri(endpoint.baseUrl + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${endpoint.authKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                return extract(responseSpec)
            } catch (e: HttpServerErrorException) {
                lastError = e                       // 5xx → 재시도
            } catch (e: ResourceAccessException) {
                lastError = e                       // 커넥션/타임아웃 → 재시도
            } catch (e: HttpClientErrorException) { // 4xx → 계약 위반, 재시도 무의미
                throw PosCommunicationException("partner rejected request: ${e.statusCode} $path", e)
            }
            if (attempt < retry.maxAttempts - 1) retry.backoffAfter(attempt)
        }
        throw PosCommunicationException(
            "partner unreachable after ${retry.maxAttempts} attempts: $path", lastError,
        )
    }
}
