package karrot.partnerpos

import karrot.partnerpos.domain.menu.application.PosStockFinder
import karrot.partnerpos.domain.menu.application.PurchaseStage
import karrot.partnerpos.domain.menu.application.StockOverlayResult
import karrot.partnerpos.domain.menu.application.StockOverlayService
import karrot.partnerpos.domain.menu.model.MenuCode
import karrot.partnerpos.domain.menu.model.MenuStock
import karrot.partnerpos.domain.order.application.OrderPlacementService
import karrot.partnerpos.domain.order.application.PartnerOrderWriter
import karrot.partnerpos.domain.order.application.PosOrderSynchronizer
import karrot.partnerpos.domain.order.application.PosOrderWriter
import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.order.model.PosOrder
import karrot.partnerpos.domain.pos.application.DirectPosPartnerRegistry
import karrot.partnerpos.domain.pos.application.PartnerRegistryReconciler
import karrot.partnerpos.domain.pos.model.DirectPosPartner
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.domain.pos.model.PartnerPolicy
import karrot.partnerpos.domain.pos.model.StockQueryable
import karrot.partnerpos.domain.pos.model.StoreRegistrable
import karrot.partnerpos.domain.store.application.ActivationResult
import karrot.partnerpos.domain.store.application.PosStoreRegistrar
import karrot.partnerpos.domain.store.application.StoreActivationService
import karrot.partnerpos.domain.store.application.StoreFinder
import karrot.partnerpos.domain.store.model.PartnerType
import karrot.partnerpos.domain.store.model.StoreCode
import karrot.partnerpos.infra.PartnerEntity
import karrot.partnerpos.infra.PartnerOrderRepository
import karrot.partnerpos.infra.PartnerRepository
import karrot.partnerpos.infra.PartnerStoreEntity
import karrot.partnerpos.infra.PartnerStoreRepository
import karrot.partnerpos.infra.StoreEntity
import karrot.partnerpos.infra.StoreRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.time.Duration.Companion.seconds

/**
 * 확장 데모 — "새 파트너 추가 = 구현 1파일 + partners row"의 증명.
 *
 * 아래 SubwayPartner는 이 테스트 파일에만 존재한다. 메인 소스의 enum·스키마·서비스·StoreFinder를
 * 단 한 줄도 수정하지 않고, 실제 H2 조립 경로(stores → partner_stores → partners → registry)부터
 * 전체 플로우(활성화 → 주문 등록 → 재고 오버레이)까지 참여한다.
 * PartnerKey가 enum이 아니라 value class인 이유가 여기서 드러난다 — 열린 집합이라
 * 공유 파일 수정 없이 새 키를 만들 수 있다.
 */
@DataJpaTest(properties = ["spring.sql.init.mode=never"])
class NewPartnerExtensionTest {

    @Autowired
    private lateinit var storeRepository: StoreRepository

    @Autowired
    private lateinit var partnerStoreRepository: PartnerStoreRepository

    @Autowired
    private lateinit var partnerRepository: PartnerRepository

    @Autowired
    private lateinit var partnerOrderRepository: PartnerOrderRepository

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

    @Test
    @DisplayName("새 파트너는 기존 코드 수정 없이 DB 조립 경로와 전체 플로우에 참여한다")
    fun newPartnerJoinsAllFlowsWithoutModification() {
        // 준비: 구현 1파일(SubwayPartner) + partners row INSERT — 추가한 것의 전부
        val subway = SubwayPartner()
        val registry = DirectPosPartnerRegistry(listOf(subway))
        val subwayRow = partnerRepository.save(PartnerEntity(name = "써브웨이", partnerKey = "SUBWAY"))

        // 기동 대사 — 코드와 DB가 일치하므로 통과
        assertDoesNotThrow { PartnerRegistryReconciler(registry, partnerRepository).reconcile() }

        // StoreFinder가 실제 H2 조인 경로로 조립 — StoreFinder 코드는 SUBWAY의 존재를 모른다
        val storeRow = storeRepository.save(StoreEntity(name = "써브웨이 서초점", partnerType = PartnerType.INTEGRATED_PARTNER))
        partnerStoreRepository.save(
            PartnerStoreEntity(storeId = storeRow.id, partnerId = subwayRow.id, partnerStoreCode = "SW-001"),
        )
        val store = StoreFinder(storeRepository, partnerStoreRepository, partnerRepository, registry).find(storeRow.id)

        // 매장 활성화 — capability(StoreRegistrable)에 따라 파트너측 등록이 활성화의 전제가 된다
        val foodTech = RecordingFoodTechClient()
        val happyOrder = RecordingHappyOrderClient()
        val activated = StoreActivationService(PosStoreRegistrar(foodTech)).activate(store)
        assertThat(activated).isEqualTo(ActivationResult.Activated)
        assertThat(subway.registeredStores).containsExactly(StoreCode("SW-001"))

        // 주문 등록 — OrderPlacementService는 SUBWAY의 존재를 모른 채 동작하고, 매핑은 실제 H2에 남는다
        val order = sampleOrder()
        OrderPlacementService(
            PosOrderWriter(PartnerOrderWriter(partnerOrderRepository, partnerRepository)),
            PosOrderSynchronizer(foodTech, happyOrder),
        ).place(store, order)
        assertThat(subway.registeredOrders).containsExactly(order)
        assertThat(partnerOrderRepository.findAll())
            .singleElement()
            .satisfies({ assertThat(it.partnerId).isEqualTo(subwayRow.id) })

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
