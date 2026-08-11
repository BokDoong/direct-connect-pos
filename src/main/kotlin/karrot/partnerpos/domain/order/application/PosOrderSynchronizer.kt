package karrot.partnerpos.domain.order.application

import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.order.model.PosOrder
import karrot.partnerpos.client.legacy.FoodTechClient
import karrot.partnerpos.client.legacy.HappyOrderClient
import karrot.partnerpos.domain.store.model.PartnerType
import karrot.partnerpos.domain.store.model.Store
import org.springframework.stereotype.Component

/**
 * 주문 등록/취소의 파트너 타입 분기 지점 (AS-IS `PartnerOrderRegistrar`/`PartnerPosCanceler` 라우터 재현).
 *
 * 직연동(INTEGRATED_PARTNER)은 통합 규격(DirectPosPartner 전략)으로, 푸드테크·해피오더는
 * 각자 규격의 레거시 클라이언트로 나간다 — 우리가 파트너 규격에 맞춰 직접 연동했던 경로라
 * 통합 인터페이스로 흡수하지 않고 분기로 호환한다.
 * enum 분기가 남지만 exhaustive when이라 새 타입 추가 시 컴파일 에러로 잡힌다.
 */
@Component
class PosOrderSynchronizer(
    private val foodTechClient: FoodTechClient,
    private val happyOrderClient: HappyOrderClient,
) {
    fun registerOrder(store: Store, order: PosOrder) {
        when (store.partnerType) {
            PartnerType.INTEGRATED_PARTNER -> checkNotNull(store.directPos).partner.registerOrder(order)
            PartnerType.FOODTECH -> foodTechClient.registerOrder(order)
            PartnerType.HAPPYORDER -> happyOrderClient.registerOrder(order)
            PartnerType.KARROT -> Unit  // 외부 시스템 없음 — 사장님이 앱에서 직접 처리
        }
    }

    fun cancelOrder(store: Store, orderCode: OrderCode) {
        when (store.partnerType) {
            PartnerType.INTEGRATED_PARTNER -> checkNotNull(store.directPos).partner.cancelOrder(orderCode)
            PartnerType.FOODTECH -> foodTechClient.cancelOrder(orderCode)
            PartnerType.HAPPYORDER -> happyOrderClient.cancelOrder(orderCode)
            PartnerType.KARROT -> Unit
        }
    }
}
