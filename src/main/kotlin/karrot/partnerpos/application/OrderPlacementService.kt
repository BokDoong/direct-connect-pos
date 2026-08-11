package karrot.partnerpos.application

import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.contract.PosOrder
import karrot.partnerpos.store.PartnerType
import karrot.partnerpos.store.Store
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 결제 완료 → 파트너 주문 등록 오케스트레이션.
 * 고객 동기 경로다 — AS-IS와 동일하게 파트너 등록 완료까지가 주문 완료의 조건.
 *
 * 파트너 결정은 이 서비스의 관심사가 아니다 — StoreFinder가 조립한 [Store]가 맥락을 물고 오고,
 * 타입별 아웃바운드 분기는 [PosOrderSynchronizer]가, 타입별 매핑 저장은 [PosOrderWriter]가 맡는다.
 */
@Service
class OrderPlacementService(
    private val posOrderWriter: PosOrderWriter,
    private val posOrderSynchronizer: PosOrderSynchronizer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun place(store: Store, order: PosOrder) {
        try {
            posOrderSynchronizer.registerOrder(store, order)
        } catch (e: Exception) {
            cancelPayment(order) // 등록 실패 → 결제 보상 (의사코드)
            throw e
        }

        try {
            posOrderWriter.write(store, order.orderCode)  // 타입별 매핑 원장 저장 분기는 Writer 소관
        } catch (e: Exception) {
            runCatching { posOrderSynchronizer.cancelOrder(store, order.orderCode) }
                .onFailure { log.error("유령주문 보상 취소 전파 실패: {}", order.orderCode.value, it) }
            cancelPayment(order)
            throw e
        }

        scheduleUnacceptedAutoCancel(order.orderCode, unacceptedAutoCancelDelay(store))
    }

    /**
     * 미수락 자동취소 대기 시간 — 직연동은 파트너 정책값(코드), 레거시 유형은 AS-IS 하드코딩 값 재현
     * (KARROT 3분 / 푸드테크·해피오더 10분).
     */
    private fun unacceptedAutoCancelDelay(store: Store): Duration = when (store.partnerType) {
        PartnerType.INTEGRATED_PARTNER -> checkNotNull(store.directPos).partner.policy.unacceptedAutoCancel
        PartnerType.KARROT -> 180.seconds
        PartnerType.FOODTECH, PartnerType.HAPPYORDER -> 600.seconds
    }

    /** 결제 취소 보상 — 당근페이 연동은 스코프 밖. 위치만 표시하는 의사코드. */
    @Suppress("UNUSED_PARAMETER")
    private fun cancelPayment(order: PosOrder) {
        // pseudocode: karrotPayClient.cancel(order.paymentId)
    }

    /** 미수락 자동취소 타이머 예약 — AS-IS: SQS 지연 메시지. 인프라 연동은 스코프 밖. */
    @Suppress("UNUSED_PARAMETER")
    private fun scheduleUnacceptedAutoCancel(orderCode: OrderCode, delay: Duration) {
        // pseudocode: sqsClient.sendDelayed(AutoCancelMessage(orderCode), delaySeconds = delay.inWholeSeconds)
    }
}
