package karrot.partnerpos.domain.store.application

import karrot.partnerpos.domain.partner.model.PosCommunicationException
import karrot.partnerpos.domain.store.model.Store
import org.springframework.stereotype.Service

sealed interface ActivationResult {
    data object Activated : ActivationResult

    /** 파트너 매장 등록 실패 → 활성화 실패 (AS-IS와 동일한 hard 의존). */
    data class Failed(val cause: Throwable) : ActivationResult
}

/**
 * 매장 활성화 — 파트너측 매장 등록이 플로우를 가르는 지점.
 *
 * 등록 지원 여부와 타입별 분기는 [PosStoreRegistrar]가 판단한다.
 * 등록이 필요한 파트너(CJ·버거킹·푸드테크)는 등록 성공이 활성화의 전제(hard 의존)이고,
 * 미지원(롯데·해피오더 — 수기 협의, KARROT — 외부 없음)은 즉시 활성화된다.
 */
@Service
class StoreActivationService(
    private val storeRegistrar: PosStoreRegistrar,
) {
    fun activate(store: Store): ActivationResult {
        try {
            storeRegistrar.registerStore(store)  // 미지원 타입은 분기에서 no-op
        } catch (e: PosCommunicationException) {
            return ActivationResult.Failed(e)    // 등록 실패 = 활성화 실패 (AS-IS 동일)
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
