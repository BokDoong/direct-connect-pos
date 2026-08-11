package karrot.partnerpos.application

import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.store.PartnerType
import karrot.partnerpos.store.Store
import org.springframework.stereotype.Component

/**
 * 주문 매핑 저장의 파트너 타입 분기 지점 — AS-IS의 타입별 매핑 side-table
 * (`partner_orders` / `foodtech_orders` / `happyorder_orders`) 구조를 레이어로 재현한다.
 *
 * 직연동만 이번 재설계 스코프의 원장(partner_orders)에 저장하고,
 * 레거시 유형의 매핑은 각자 경로 소관이라 위치만 표시한다. (AS-IS 재구성 — 정확한 로직은 기억 기반 가정)
 */
@Component
class PosOrderWriter(
    private val partnerOrderWriter: PartnerOrderWriter,
) {
    fun write(store: Store, orderCode: OrderCode) {
        when (store.partnerType) {
            PartnerType.INTEGRATED_PARTNER ->
                partnerOrderWriter.write(orderCode, checkNotNull(store.directPos).partner.key)
            PartnerType.FOODTECH -> Unit        // pseudocode: foodtechOrderWriter.write(orderCode, unidomOrderId) — foodtech_orders
            PartnerType.HAPPYORDER -> Unit      // pseudocode: happyOrderOrderWriter.write(hoOrderId) — happyorder_orders (파트너 채번 회신 저장)
            PartnerType.KARROT -> Unit          // 외부 주문번호 매핑 없음
        }
    }
}
