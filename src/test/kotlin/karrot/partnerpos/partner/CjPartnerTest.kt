package karrot.partnerpos.partner

import karrot.partnerpos.BoundTransport
import karrot.partnerpos.contract.MenuCode
import karrot.partnerpos.contract.MenuStock
import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.contract.StoreCode
import karrot.partnerpos.propertiesFor
import karrot.partnerpos.sampleOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

/** CJ 계약 테스트 — 이 파트너가 실제로 어떤 요청을 만드는지 검증한다. */
class CjPartnerTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var partner: CjPartner
    private val baseUrl = "https://cj-foodville.example.com"

    @BeforeEach
    fun setUp() {
        val bound = BoundTransport.create()
        server = bound.server
        partner = CjPartner(bound.transport, propertiesFor(CjPartner.KEY))
    }

    @Test
    @DisplayName("주문 등록은 당근 공통 규격 페이로드를 그대로 전송한다")
    fun registerOrderUsesCommonSpec() {
        server.expect(requestTo("$baseUrl${CjPartner.REGISTER_ORDER_PATH}"))
            .andExpect(jsonPath("$.orderCode").value("2608110000ABCD"))
            .andExpect(jsonPath("$.storeCode").value("STORE-001"))
            .andExpect(jsonPath("$.totalAmount").value(22_000))
            .andExpect(jsonPath("$.items[0].menuCode").value("MENU-A"))
            .andExpect(jsonPath("$.paymentMethod").doesNotExist()) // 공통 규격에는 결제수단이 없다
            .andRespond(withSuccess())

        partner.registerOrder(sampleOrder())

        server.verify()
    }

    @Test
    @DisplayName("취소 전파는 path와 body 모두 당근 order_code를 쓰고, 사유는 '가게 사정' 고정이다")
    fun cancelOrderUsesOrderCode() {
        server.expect(requestTo("$baseUrl/api/v1/karrot-pickup/orders/2608110000ABCD/cancel"))
            .andExpect(jsonPath("$.orderCode").value("2608110000ABCD"))
            .andExpect(jsonPath("$.reason").value("가게 사정"))
            .andRespond(withSuccess())

        partner.cancelOrder(OrderCode("2608110000ABCD"))

        server.verify()
    }

    @Test
    @DisplayName("재고 조회는 메뉴코드 리스트를 보내고 (메뉴코드+수량) 리스트를 돌려받는다")
    fun fetchStocksParsesQuantities() {
        server.expect(requestTo("$baseUrl${CjPartner.STOCKS_PATH}"))
            .andExpect(jsonPath("$.storeCode").value("STORE-001"))
            .andExpect(jsonPath("$.menuCodes[0]").value("MENU-A"))
            .andRespond(
                withSuccess(
                    """[{"menuCode":"MENU-A","quantity":3},{"menuCode":"MENU-B","quantity":0}]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val stocks = partner.fetchStocks(StoreCode("STORE-001"), listOf(MenuCode("MENU-A"), MenuCode("MENU-B")))

        assertThat(stocks).containsExactly(
            MenuStock(MenuCode("MENU-A"), 3),
            MenuStock(MenuCode("MENU-B"), 0),
        )
        server.verify()
    }

    @Test
    @DisplayName("매장 등록/해지는 겸용 엔드포인트에 storeCode와 구분값을 담아 보낸다")
    fun storeRegistration() {
        server.expect(requestTo("$baseUrl${CjPartner.STORE_REGISTRATION_PATH}"))
            .andExpect(jsonPath("$.storeCode").value("STORE-001"))
            .andExpect(jsonPath("$.action").value("REGISTER"))
            .andRespond(withSuccess())

        partner.registerStore(StoreCode("STORE-001"))

        server.verify()
    }
}
