package karrot.partnerpos.client.direct

import karrot.partnerpos.config.DirectPosProperties
import karrot.partnerpos.domain.pos.model.DirectPosPartner
import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.domain.pos.model.PartnerPolicy
import karrot.partnerpos.domain.order.model.PosOrder
import karrot.partnerpos.client.transport.PosApiTransport
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.seconds

/**
 * 롯데GRS (롯데리아·엔제리너스·크리스피크림) — 최소셋 파트너.
 *
 * 선언이 곧 명세다: 필수 계약(주문 등록/취소)만 구현한다.
 * 재고 조회 미지원(품절 신호는 인바운드로만), 매장 등록 API 없음(수기 협의),
 * 미수락 자동취소 300초(계약상 5분 — 전 파트너 중 최단).
 */
@Component
class LottePartner(
    private val transport: PosApiTransport,
    props: DirectPosProperties,
) : DirectPosPartner {

    override val key = KEY
    override val policy = PartnerPolicy(unacceptedAutoCancel = 300.seconds)
    private val endpoint = props.endpointOf(KEY)

    override fun registerOrder(order: PosOrder) {
        transport.post(endpoint, REGISTER_ORDER_PATH, CommonOrderPayload.from(order))
    }

    override fun cancelOrder(orderCode: OrderCode) {
        transport.post(endpoint, cancelOrderPath(orderCode), OrderCancelPayload(orderCode.value))
    }

    companion object {
        val KEY = PartnerKey("LOTTE_GRS")

        const val REGISTER_ORDER_PATH = "/api/v1/karrot-pickup/orders/register"

        fun cancelOrderPath(orderCode: OrderCode) = "/api/v1/karrot-pickup/orders/${orderCode.value}/cancel"
    }
}
