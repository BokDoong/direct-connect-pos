package karrot.partnerpos.client.direct

import karrot.partnerpos.BoundTransport
import karrot.partnerpos.propertiesFor
import karrot.partnerpos.sampleOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

/**
 * 버거킹 계약 테스트 — 페이로드 확장(AS-IS의 임계점)이 타입 안전하게 흡수됐는지 검증한다.
 * 공통 규격 필드는 그대로 유지되고, 결제수단 필드가 최상위에 추가된다.
 */
class BurgerKingPartnerTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var partner: BurgerKingPartner
    private val baseUrl = "https://burger-king.example.com"

    @BeforeEach
    fun setUp() {
        val bound = BoundTransport.create()
        server = bound.server
        partner = BurgerKingPartner(bound.transport, propertiesFor(BurgerKingPartner.KEY))
    }

    @Test
    @DisplayName("주문 등록 페이로드 = 공통 규격 + 결제수단 필드 (당근페이 결제수단의 버거킹 코드 매핑)")
    fun registerOrderCarriesPaymentMethod() {
        server.expect(requestTo("$baseUrl${BurgerKingPartner.REGISTER_ORDER_PATH}"))
            // 공통 규격 필드는 그대로 —
            .andExpect(jsonPath("$.orderCode").value("2608110000ABCD"))
            .andExpect(jsonPath("$.storeCode").value("STORE-001"))
            .andExpect(jsonPath("$.totalAmount").value(22_000))
            // — 버거킹 확장 필드가 추가된다
            .andExpect(jsonPath("$.paymentMethod").value("MONEY"))
            .andRespond(withSuccess())

        partner.registerOrder(sampleOrder())

        server.verify()
    }
}
