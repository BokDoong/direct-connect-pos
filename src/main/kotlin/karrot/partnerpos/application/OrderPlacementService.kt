package karrot.partnerpos.application

import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.contract.PosOrder
import karrot.partnerpos.store.Store
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.time.Duration

/**
 * 결제 완료 → 파트너 주문 등록 오케스트레이션.
 * 고객 동기 경로다 — AS-IS와 동일하게 파트너 등록 완료까지가 주문 완료의 조건.
 *
 * 파트너 결정은 이 서비스의 관심사가 아니다 — StoreFinder가 조립한 [Store]가 파트너를 물고 온다.
 * 저장도 이 서비스의 관심사가 아니다 — 쓰기 구현체([PartnerOrderWriter])를 경유한다.
 */
@Service
class OrderPlacementService(
    private val partnerOrderWriter: PartnerOrderWriter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun place(store: Store, order: PosOrder) {
        val partner = checkNotNull(store.directPos) { "not an integrated-partner store: ${store.id}" }.partner

        try {
            partner.registerOrder(order)
        } catch (e: Exception) {
            cancelPayment(order)
            throw e
        }

        try {
            partnerOrderWriter.write(order.orderCode, partner.key)
        } catch (e: Exception) {
            runCatching { partner.cancelOrder(order.orderCode) }
                .onFailure { log.error("유령주문 보상 취소 전파 실패: {}", order.orderCode.value, it) }
            cancelPayment(order)
            throw e
        }

        scheduleUnacceptedAutoCancel(order.orderCode, partner.policy.unacceptedAutoCancel)
    }

    /** 결제 취소 보상 — 당근페이 연동은 스코프 밖. 위치만 표시하는 의사코드. */
    @Suppress("UNUSED_PARAMETER")
    private fun cancelPayment(order: PosOrder) {
        // pseudocode: karrotPayClient.cancel(order.paymentId)
    }

    /**
     * 미수락 자동취소 타이머 예약 — AS-IS: SQS 지연 메시지, delay = 파트너 정책값.
     * 인프라 연동은 스코프 밖. 파트너 정책값(코드)이 여기로 흘러드는 경로만 보인다.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun scheduleUnacceptedAutoCancel(orderCode: OrderCode, delay: Duration) {
        // pseudocode: sqsClient.sendDelayed(AutoCancelMessage(orderCode), delaySeconds = delay.inWholeSeconds)
    }
}
