package karrot.partnerpos.client.transport

import karrot.partnerpos.BoundTransport
import karrot.partnerpos.domain.pos.model.PosCommunicationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

/**
 * 변하지 않는 전송 규약(AS-IS 재현)의 검증:
 * Bearer 인증 · HTTP status만으로 판정 · 5xx 한정 재시도 4회 · 4xx 즉시 실패.
 */
class PosApiTransportTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var transport: PosApiTransport
    private val endpoint = PosEndpoint(baseUrl = "https://pos.example.com", authKey = "test-key")

    @BeforeEach
    fun setUp() {
        val bound = BoundTransport.create()
        server = bound.server
        transport = bound.transport
    }

    @Test
    @DisplayName("Bearer 인증 헤더와 JSON content-type으로 호출한다")
    fun authHeader() {
        server.expect(requestTo("https://pos.example.com/orders/register"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
            .andRespond(withSuccess())

        transport.post(endpoint, "/orders/register", mapOf("orderCode" to "X"))

        server.verify()
    }

    @Test
    @DisplayName("5xx는 4회까지 재시도한 뒤 PosCommunicationException으로 실패한다")
    fun serverErrorRetriesFourTimes() {
        repeat(4) {
            server.expect(requestTo("https://pos.example.com/orders/register"))
                .andRespond(withServerError())
        }

        assertThrows<PosCommunicationException> {
            transport.post(endpoint, "/orders/register", mapOf("orderCode" to "X"))
        }

        server.verify() // 정확히 4회 호출됐음을 검증
    }

    @Test
    @DisplayName("5xx 후 성공 응답이 오면 재시도로 회복한다 — order_code 멱등 계약이 재시도의 안전 근거")
    fun recoversByRetry() {
        server.expect(requestTo("https://pos.example.com/orders/register")).andRespond(withServerError())
        server.expect(requestTo("https://pos.example.com/orders/register")).andRespond(withServerError())
        server.expect(requestTo("https://pos.example.com/orders/register")).andRespond(withSuccess())

        transport.post(endpoint, "/orders/register", mapOf("orderCode" to "X"))

        server.verify()
    }

    @Test
    @DisplayName("4xx는 계약 위반이므로 재시도 없이 즉시 실패한다")
    fun clientErrorFailsFast() {
        server.expect(requestTo("https://pos.example.com/orders/register"))
            .andRespond(withBadRequest())

        val thrown = assertThrows<PosCommunicationException> {
            transport.post(endpoint, "/orders/register", mapOf("orderCode" to "X"))
        }

        assertThat(thrown.message).contains("rejected")
        server.verify() // 1회만 호출
    }
}
