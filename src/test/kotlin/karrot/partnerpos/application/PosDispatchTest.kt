package karrot.partnerpos.application

import karrot.partnerpos.RecordingFoodTechClient
import karrot.partnerpos.RecordingHappyOrderClient
import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.contract.MenuCode
import karrot.partnerpos.contract.MenuStock
import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.contract.StockQueryable
import karrot.partnerpos.contract.StoreCode
import karrot.partnerpos.contract.StoreRegistrable
import karrot.partnerpos.foodTechStore
import karrot.partnerpos.happyOrderStore
import karrot.partnerpos.integratedStore
import karrot.partnerpos.karrotStore
import karrot.partnerpos.sampleOrder
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

    @Nested
    inner class OrderWriter {
        private val savedMappings = mutableMapOf<OrderCode, PartnerKey>()
        private val writer = PosOrderWriter(
            PartnerOrderWriter(object : PartnerOrderMappingRepository {
                override fun save(orderCode: OrderCode, partnerKey: PartnerKey) {
                    savedMappings[orderCode] = partnerKey
                }
            }),
        )

        @Test
        @DisplayName("직연동 매장만 partner_orders 매핑에 저장된다 — 레거시 매핑은 각자 경로 소관")
        fun onlyIntegratedWritesMapping() {
            val orderCode = OrderCode("2608110000ABCD")

            writer.write(integratedStore(RecordingPartner(key = PartnerKey("DIRECT"))), orderCode)
            writer.write(foodTechStore(), orderCode)
            writer.write(happyOrderStore(), orderCode)
            writer.write(karrotStore(), orderCode)

            assertThat(savedMappings).containsExactlyEntriesOf(mapOf(orderCode to PartnerKey("DIRECT")))
        }
    }

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
