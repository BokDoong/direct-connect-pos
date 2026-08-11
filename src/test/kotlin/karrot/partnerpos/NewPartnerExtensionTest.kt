package karrot.partnerpos

import karrot.partnerpos.application.ActivationResult
import karrot.partnerpos.application.InMemoryPartnerOrderMappingRepository
import karrot.partnerpos.application.OrderPlacementService
import karrot.partnerpos.application.PartnerOrderWriter
import karrot.partnerpos.application.PosOrderSynchronizer
import karrot.partnerpos.application.PosOrderWriter
import karrot.partnerpos.application.PosStockFinder
import karrot.partnerpos.application.PosStoreRegistrar
import karrot.partnerpos.application.PurchaseStage
import karrot.partnerpos.application.StockOverlayService
import karrot.partnerpos.application.StockOverlayResult
import karrot.partnerpos.application.StoreActivationService
import karrot.partnerpos.contract.DirectPosPartner
import karrot.partnerpos.contract.DirectPosPartnerRegistry
import karrot.partnerpos.contract.MenuCode
import karrot.partnerpos.contract.MenuStock
import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.contract.PartnerPolicy
import karrot.partnerpos.contract.PosOrder
import karrot.partnerpos.contract.StockQueryable
import karrot.partnerpos.contract.StoreCode
import karrot.partnerpos.contract.StoreRegistrable
import karrot.partnerpos.store.PartnerRecordRepository
import karrot.partnerpos.store.PartnerRecord
import karrot.partnerpos.store.PartnerRegistryReconciler
import karrot.partnerpos.store.PartnerStoreLink
import karrot.partnerpos.store.PartnerType
import karrot.partnerpos.store.InMemoryPartnerStoreLinkRepository
import karrot.partnerpos.store.InMemoryStoreRecordRepository
import karrot.partnerpos.store.StoreFinder
import karrot.partnerpos.store.StoreRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.time.Duration.Companion.seconds

/**
 * 확장 데모 — "새 파트너 추가 = 구현 1파일 + partners row"의 증명.
 *
 * 아래 SubwayPartner는 이 테스트 파일에만 존재한다. 메인 소스의 enum·스키마·서비스·StoreFinder를
 * 단 한 줄도 수정하지 않고, DB 조립 경로(stores → partner_stores → partners → registry)부터
 * 전체 플로우(활성화 → 주문 등록 → 재고 오버레이)까지 참여한다.
 * PartnerKey가 enum이 아니라 value class인 이유가 여기서 드러난다 — 열린 집합이라
 * 공유 파일 수정 없이 새 키를 만들 수 있다.
 */
class NewPartnerExtensionTest {

    /** 가상의 제4 직연동 파트너 — 재고 조회·매장 등록을 지원하고 자동취소 8분을 계약했다고 가정. */
    private class SubwayPartner : DirectPosPartner, StockQueryable, StoreRegistrable {
        override val key = PartnerKey("SUBWAY")
        override val policy = PartnerPolicy(unacceptedAutoCancel = 480.seconds)

        val registeredOrders = mutableListOf<PosOrder>()
        val registeredStores = mutableListOf<StoreCode>()

        override fun registerOrder(order: PosOrder) {
            registeredOrders += order
        }

        override fun cancelOrder(orderCode: OrderCode) = Unit

        override fun fetchStocks(storeCode: StoreCode, menuCodes: List<MenuCode>): List<MenuStock> =
            menuCodes.map { MenuStock(it, quantity = 7) }

        override fun registerStore(storeCode: StoreCode) {
            registeredStores += storeCode
        }

        override fun unregisterStore(storeCode: StoreCode) = Unit
    }

    /** partners 테이블 더블 — SUBWAY row가 INSERT된 상태 (name은 표시용, key가 dispatch 식별자). */
    private class SubwayPartnerRecords : PartnerRecordRepository {
        private val record = PartnerRecord(id = 99L, name = "써브웨이", key = "SUBWAY")
        override fun getById(partnerId: Long): PartnerRecord = record
        override fun findAllKeys(): List<String> = listOf(record.key)
    }

    @Test
    @DisplayName("새 파트너는 기존 코드 수정 없이 DB 조립 경로와 전체 플로우에 참여한다")
    fun newPartnerJoinsAllFlowsWithoutModification() {
        // 준비: 구현 1파일(SubwayPartner) + partners row(SubwayPartnerRecords) — 추가한 것의 전부
        val subway = SubwayPartner()
        val registry = DirectPosPartnerRegistry(listOf(subway))
        val partnerRecords = SubwayPartnerRecords()

        // 기동 대사 — 코드와 DB가 일치하므로 통과
        assertDoesNotThrow { PartnerRegistryReconciler(registry, partnerRecords).reconcile() }

        // StoreFinder가 A안 경로로 조립 — StoreFinder 코드는 SUBWAY의 존재를 모른다
        val storeRecords = InMemoryStoreRecordRepository()
            .apply { save(StoreRecord(id = 1L, name = "써브웨이 서초점", partnerType = PartnerType.INTEGRATED_PARTNER)) }
        val links = InMemoryPartnerStoreLinkRepository()
            .apply { save(PartnerStoreLink(storeId = 1L, partnerId = 99L, partnerStoreCode = "SW-001")) }
        val store = StoreFinder(storeRecords, links, partnerRecords, registry).find(1L)

        // 매장 활성화 — capability(StoreRegistrable)에 따라 파트너측 등록이 활성화의 전제가 된다
        val foodTech = RecordingFoodTechClient()
        val happyOrder = RecordingHappyOrderClient()
        val activated = StoreActivationService(PosStoreRegistrar(foodTech)).activate(store)
        assertThat(activated).isEqualTo(ActivationResult.Activated)
        assertThat(subway.registeredStores).containsExactly(StoreCode("SW-001"))

        // 주문 등록 — OrderPlacementService는 SUBWAY의 존재를 모른 채 동작한다
        val order = sampleOrder()
        OrderPlacementService(
            PosOrderWriter(PartnerOrderWriter(InMemoryPartnerOrderMappingRepository())),
            PosOrderSynchronizer(foodTech, happyOrder),
        ).place(store, order)
        assertThat(subway.registeredOrders).containsExactly(order)

        // 재고 오버레이 — capability(StockQueryable 구현)도 자동으로 인식된다
        val result = StockOverlayService(PosStockFinder(happyOrder))
            .overlay(store, listOf(MenuCode("MENU-A")), PurchaseStage.MENU_VIEW)
        assertThat(result).isEqualTo(
            StockOverlayResult.Overlaid(listOf(MenuStock(MenuCode("MENU-A"), 7))),
        )

        // 정책값도 구현체 선언이 그대로 흘러든다
        assertThat(store.directPos!!.partner.policy.unacceptedAutoCancel).isEqualTo(480.seconds)
    }
}
