package karrot.partnerpos

import karrot.partnerpos.domain.menu.application.PosStockFinder
import karrot.partnerpos.domain.order.application.PosOrderSynchronizer
import karrot.partnerpos.domain.store.application.PosStoreRegistrar
import karrot.partnerpos.domain.menu.model.MenuCode
import karrot.partnerpos.domain.menu.model.MenuStock
import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.domain.pos.model.StockQueryable
import karrot.partnerpos.domain.store.model.StoreCode
import karrot.partnerpos.domain.pos.model.StoreRegistrable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 파트너 타입 분기 컴포넌트 3종의 라우팅 검증 —
 * 직연동은 전략(DirectPosPartner)으로, 푸드테크·해피오더는 레거시 클라이언트로, KARROT은 no-op.
 */
class PosDispatchTest {

    private val foodTech = RecordingFoodTechClient()
    private val happyOrder = RecordingHappyOrderClient()

    private class StockPartner : RecordingPartner(key = PartnerKey("STOCK")), StockQueryable {
        override fun fetchStocks(storeCode: StoreCode, menuCodes: List<MenuCode>): List<MenuStock> =
            menuCodes.map { MenuStock(it, 5) }
    }

    private class RegistrablePartner : RecordingPartner(key = PartnerKey("REGISTRABLE")), StoreRegistrable {
        val registeredStores = mutableListOf<StoreCode>()
        override fun registerStore(storeCode: StoreCode) { registeredStores += storeCode }
        override fun unregisterStore(storeCode: StoreCode) = Unit
    }

    @Nested
    inner class OrderSynchronizer {
        private val synchronizer = PosOrderSynchronizer(foodTech, happyOrder)

        @Test
        @DisplayName("직연동 매장은 조립된 DirectPosPartner로 등록/취소가 나간다")
        fun routesIntegratedToDirectPartner() {
            val partner = RecordingPartner(key = PartnerKey("DIRECT"))
            val store = integratedStore(partner)
            val order = sampleOrder()

            synchronizer.registerOrder(store, order)
            synchronizer.cancelOrder(store, order.orderCode)

            assertThat(partner.registeredOrders).containsExactly(order)
            assertThat(partner.canceledOrderCodes).containsExactly(order.orderCode)
        }

        @Test
        @DisplayName("푸드테크·해피오더 매장은 각자의 레거시 클라이언트로 나간다")
        fun routesLegacyTypesToLegacyClients() {
            val order = sampleOrder()

            synchronizer.registerOrder(foodTechStore(), order)
            synchronizer.registerOrder(happyOrderStore(), order)

            assertThat(foodTech.registeredOrders).containsExactly(order)
            assertThat(happyOrder.registeredOrders).containsExactly(order)
        }

        @Test
        @DisplayName("KARROT 매장은 외부 시스템이 없어 아무 데도 나가지 않는다")
        fun karrotIsNoOp() {
            synchronizer.registerOrder(karrotStore(), sampleOrder())
            synchronizer.cancelOrder(karrotStore(), OrderCode("2608110000ABCD"))

            assertThat(foodTech.registeredOrders).isEmpty()
            assertThat(happyOrder.registeredOrders).isEmpty()
        }
    }

    // 주문 매핑 쓰기(PosOrderWriter)의 분기는 실제 H2 제약과 함께 PosOrderWriterTest에서 검증한다.

    @Nested
    inner class StockFinder {
        private val finder = PosStockFinder(happyOrder)
        private val menuCodes = listOf(MenuCode("MENU-A"))

        @Test
        @DisplayName("직연동은 capability(StockQueryable)로 판단한다 — 구현체면 조회, 아니면 null")
        fun integratedUsesCapability() {
            assertThat(finder.findStocks(integratedStore(StockPartner()), menuCodes))
                .containsExactly(MenuStock(MenuCode("MENU-A"), 5))
            assertThat(finder.findStocks(integratedStore(RecordingPartner()), menuCodes)).isNull()
        }

        @Test
        @DisplayName("해피오더는 레거시 클라이언트로 조회하고, 푸드테크(푸시형)·KARROT은 null")
        fun legacyTypesRouting() {
            happyOrder.stocks = listOf(MenuStock(MenuCode("MENU-A"), 2))

            assertThat(finder.findStocks(happyOrderStore(), menuCodes))
                .containsExactly(MenuStock(MenuCode("MENU-A"), 2))
            assertThat(finder.findStocks(foodTechStore(), menuCodes)).isNull()
            assertThat(finder.findStocks(karrotStore(), menuCodes)).isNull()
        }
    }

    @Nested
    inner class StoreRegistrar {
        private val registrar = PosStoreRegistrar(foodTech)

        @Test
        @DisplayName("직연동은 capability(StoreRegistrable)로 판단해 파트너측 매장 코드로 등록한다")
        fun integratedUsesCapability() {
            val partner = RegistrablePartner()

            registrar.registerStore(integratedStore(partner, partnerStoreCode = "CJ-042"))
            registrar.registerStore(integratedStore(RecordingPartner()))  // 미지원 — no-op

            assertThat(partner.registeredStores).containsExactly(StoreCode("CJ-042"))
        }

        @Test
        @DisplayName("푸드테크는 레거시 클라이언트로 연동 등록하고, 해피오더(수기)·KARROT은 no-op")
        fun legacyTypesRouting() {
            registrar.registerStore(foodTechStore())
            registrar.registerStore(happyOrderStore())
            registrar.registerStore(karrotStore())

            assertThat(foodTech.linkedStoreIds).containsExactly(3L)
        }
    }
}
