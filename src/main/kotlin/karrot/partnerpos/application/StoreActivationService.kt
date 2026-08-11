package karrot.partnerpos.application

import karrot.partnerpos.contract.DirectPosPartnerRegistry
import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.contract.PosCommunicationException
import karrot.partnerpos.contract.StoreCode
import karrot.partnerpos.contract.StoreRegistrable
import org.springframework.stereotype.Service

sealed interface ActivationResult {
    data object Activated : ActivationResult

    /** 파트너 매장 등록 실패 → 활성화 실패 (AS-IS와 동일한 hard 의존). */
    data class Failed(val cause: Throwable) : ActivationResult
}

/**
 * 매장 활성화 — capability가 플로우를 가르는 지점.
 *
 * StoreRegistrable 파트너(CJ·버거킹)는 파트너측 매장 등록 성공이 활성화의 전제이고,
 * 미지원 파트너(롯데 — 수기 협의)는 즉시 활성화된다.
 */
@Service
class StoreActivationService(
    private val registry: DirectPosPartnerRegistry,
) {
    fun activate(partnerKey: PartnerKey, storeCode: StoreCode): ActivationResult {
        val partner = registry[partnerKey]

        if (partner is StoreRegistrable) {
            try {
                partner.registerStore(storeCode)
            } catch (e: PosCommunicationException) {
                return ActivationResult.Failed(e)
            }
        }

        markStoreActive(storeCode)
        return ActivationResult.Activated
    }

    /** stores.status = ACTIVE 저장 — 매장 도메인은 스코프 밖, 위치만 표시하는 의사코드. */
    @Suppress("UNUSED_PARAMETER")
    private fun markStoreActive(storeCode: StoreCode) {
        // pseudocode: storeRepository.updateStatus(storeCode, ACTIVE)
    }
}
