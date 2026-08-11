package karrot.partnerpos.application

import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.contract.DirectPosPartnerRegistry
import karrot.partnerpos.contract.MenuCode
import karrot.partnerpos.contract.MenuStock
import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.contract.PosCommunicationException
import karrot.partnerpos.contract.StockQueryable
import karrot.partnerpos.contract.StoreCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StockOverlayServiceTest {

    /** 재고 지원 파트너 더블 — capability는 인터페이스 구현으로 표현된다. */
    private class StockPartner(
        var stocks: List<MenuStock> = emptyList(),
        var failOnFetch: Boolean = false,
    ) : RecordingPartner(key = PartnerKey("STOCK_PARTNER")), StockQueryable {
        override fun fetchStocks(storeCode: StoreCode, menuCodes: List<MenuCode>): List<MenuStock> {
            if (failOnFetch) throw PosCommunicationException("stock fetch failed (stub)")
            return stocks
        }
    }

    private val stockless = RecordingPartner(key = PartnerKey("STOCKLESS_PARTNER"))
    private val stockPartner = StockPartner()
    private val service = StockOverlayService(DirectPosPartnerRegistry(listOf(stockless, stockPartner)))

    private val menuCodes = listOf(MenuCode("MENU-A"))
    private val storeCode = StoreCode("STORE-001")

    @Test
    @DisplayName("재고 미지원 파트너(StockQueryable 미구현)는 어느 단계든 DB 값을 쓴다")
    fun stocklessPartnerFallsBackToDb() {
        PurchaseStage.entries.forEach { stage ->
            val result = service.overlay(stockless.key, storeCode, menuCodes, stage)
            assertThat(result).isEqualTo(StockOverlayResult.FromDb)
        }
    }

    @Test
    @DisplayName("재고 지원 파트너는 실시간 수량으로 오버레이한다")
    fun overlaysRealtimeStocks() {
        stockPartner.stocks = listOf(MenuStock(MenuCode("MENU-A"), 3))

        val result = service.overlay(stockPartner.key, storeCode, menuCodes, PurchaseStage.MENU_VIEW)

        assertThat(result).isEqualTo(StockOverlayResult.Overlaid(listOf(MenuStock(MenuCode("MENU-A"), 3))))
    }

    @Test
    @DisplayName("조회 실패 시 화면 조회 단계는 soft — DB 값으로 폴백한다")
    fun softFailureOnViewStages() {
        stockPartner.failOnFetch = true

        listOf(PurchaseStage.MENU_VIEW, PurchaseStage.MENU_DETAIL).forEach { stage ->
            val result = service.overlay(stockPartner.key, storeCode, menuCodes, stage)
            assertThat(result).isEqualTo(StockOverlayResult.FromDb)
        }
    }

    @Test
    @DisplayName("조회 실패 시 담기·주문·결제 단계는 hard — 차단한다")
    fun hardFailureOnPurchaseStages() {
        stockPartner.failOnFetch = true

        listOf(PurchaseStage.CART_ADD, PurchaseStage.ORDER_CREATE, PurchaseStage.PAYMENT).forEach { stage ->
            val result = service.overlay(stockPartner.key, storeCode, menuCodes, stage)
            assertThat(result).isEqualTo(StockOverlayResult.Blocked)
        }
    }

    @Test
    @DisplayName("조회 실패 시 장바구니 조회 단계는 해당 메뉴를 품절 처리한다")
    fun cartViewClosesMenus() {
        stockPartner.failOnFetch = true

        val result = service.overlay(stockPartner.key, storeCode, menuCodes, PurchaseStage.CART_VIEW)

        assertThat(result).isEqualTo(StockOverlayResult.MenusClosed(menuCodes))
    }
}
