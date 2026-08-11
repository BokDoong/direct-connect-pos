package karrot.partnerpos.application

import karrot.partnerpos.contract.PosCommunicationException
import karrot.partnerpos.contract.StoreRegistrable
import karrot.partnerpos.store.PartnerType
import karrot.partnerpos.store.Store
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
class StoreActivationService {
    fun activate(store: Store): ActivationResult {
        if (store.partnerType == PartnerType.INTEGRATED_PARTNER) {
            val partner = store.directPosPartner()
            if (partner is StoreRegistrable) {
                try {
                    partner.registerStore(store.partnerStoreCode())  // 파트너측 매장 코드로 등록
                } catch (e: PosCommunicationException) {
                    return ActivationResult.Failed(e)
                }
            }
        }

        markStoreActive(store.id)
        return ActivationResult.Activated
    }

    /** stores.status = ACTIVE 저장 — 매장 도메인은 스코프 밖, 위치만 표시하는 의사코드. */
    @Suppress("UNUSED_PARAMETER")
    private fun markStoreActive(storeId: Long) {
        // pseudocode: storeRepository.updateStatus(storeId, ACTIVE)
    }
}
