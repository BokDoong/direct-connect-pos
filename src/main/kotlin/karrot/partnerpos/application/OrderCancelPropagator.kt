package karrot.partnerpos.application

import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.store.Store
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 당근발 취소의 POS 전파 (AS-IS `PartnerPosCanceler`).
 *
 * 호출 전제: 당근의 취소+환불은 이미 확정된 상태다. 전파는 best-effort —
 * 실패해도 당근 취소는 유지되고 로깅(AS-IS: Sentry)만 남긴다.
 * 매장발 취소는 인바운드로 들어오므로 이 컴포넌트의 대상이 아니다.
 */
@Service
class OrderCancelPropagator {
    private val log = LoggerFactory.getLogger(javaClass)

    fun propagate(store: Store, orderCode: OrderCode) {
        val partner = store.directPosPartner ?: return  // 직연동이 아니면 전파할 곳이 없다

        runCatching { partner.cancelOrder(orderCode) }
            .onFailure { log.warn("취소 전파 실패 — 당근 취소는 유지 (best-effort): {}", orderCode.value, it) }
    }
}
