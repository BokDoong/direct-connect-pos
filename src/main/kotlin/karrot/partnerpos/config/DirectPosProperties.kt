package karrot.partnerpos.config

import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.transport.PosEndpoint
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 코드가 아닌 유일한 것 — 환경 데이터(base_url)와 시크릿(auth_key)의 바인딩.
 * AS-IS `partners` 테이블에서 라우팅·시크릿 성격의 컬럼만 여기 남고,
 * capability·정책값은 각 파트너 구현체 코드로 승격됐다 (하이브리드 원칙의 경계).
 */
@ConfigurationProperties("partner-pos")
data class DirectPosProperties(
    val endpoints: Map<String, EndpointProperties> = emptyMap(),
) {
    data class EndpointProperties(
        val baseUrl: String,
        /** 실환경에서는 시크릿 스토어/환경변수로 주입한다 — AS-IS 평문 DB 저장(P7)의 교정. */
        val authKey: String,
    )

    fun endpointOf(key: PartnerKey): PosEndpoint {
        val endpoint = requireNotNull(endpoints[key.name]) {
            "no endpoint configured for partner '${key.name}' — check partner-pos.endpoints in yml"
        }
        return PosEndpoint(baseUrl = endpoint.baseUrl, authKey = endpoint.authKey)
    }
}
