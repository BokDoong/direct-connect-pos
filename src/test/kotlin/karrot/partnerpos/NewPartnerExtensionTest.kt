package karrot.partnerpos

import karrot.partnerpos.application.ActivationResult
import karrot.partnerpos.application.InMemoryPartnerOrderMappingRepository
import karrot.partnerpos.application.OrderPlacementService
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * 확장 데모 — "새 파트너 추가 = 구현 1파일"의 증명.
 *
 * 아래 SubwayPartner는 이 테스트 파일에만 존재한다. 메인 소스의 enum·스키마·서비스 코드를
 * 단 한 줄도 수정하지 않고, 구현 클래스 하나로 전체 플로우(주문 등록, 재고 오버레이)에 참여한다.
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

    @Test
    @DisplayName("새 파트너는 기존 코드 수정 없이 주문 등록·재고 오버레이 플로우에 참여한다")
    fun newPartnerJoinsAllFlowsWithoutModification() {
        val subway = SubwayPartner()
        val registry = DirectPosPartnerRegistry(listOf(subway))

        // 매장 활성화 — capability(StoreRegistrable)에 따라 파트너측 등록이 활성화의 전제가 된다
        val activation = StoreActivationService(registry)
        val activated = activation.activate(subway.key, StoreCode("STORE-001"))
        assertThat(activated).isEqualTo(ActivationResult.Activated)
        assertThat(subway.registeredStores).containsExactly(StoreCode("STORE-001"))

        // 주문 등록 — OrderPlacementService는 SUBWAY의 존재를 모른 채 동작한다
        val placement = OrderPlacementService(registry, InMemoryPartnerOrderMappingRepository())
        val order = sampleOrder()
        placement.place(subway.key, order)
        assertThat(subway.registeredOrders).containsExactly(order)

        // 재고 오버레이 — capability(StockQueryable 구현)도 자동으로 인식된다
        val overlay = StockOverlayService(registry)
        val result = overlay.overlay(
            subway.key, StoreCode("STORE-001"), listOf(MenuCode("MENU-A")), PurchaseStage.MENU_VIEW,
        )
        assertThat(result).isEqualTo(
            StockOverlayResult.Overlaid(listOf(MenuStock(MenuCode("MENU-A"), 7))),
        )

        // 정책값도 구현체 선언이 그대로 흘러든다
        assertThat(registry[subway.key].policy.unacceptedAutoCancel).isEqualTo(480.seconds)
    }
}
