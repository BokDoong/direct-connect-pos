package karrot.partnerpos.domain.store.application

import karrot.partnerpos.domain.pos.model.StoreRegistrable
import karrot.partnerpos.client.legacy.FoodTechClient
import karrot.partnerpos.domain.store.model.PartnerType
import karrot.partnerpos.domain.store.model.Store
import org.springframework.stereotype.Component

/**
 * 매장 등록/해지의 파트너 타입 분기 지점 (AS-IS `PartnerStoreActivator`/`FoodTechStoreActivator` 재현).
 *
 * 직연동은 capability(StoreRegistrable 구현 여부)로, 푸드테크는 레거시 클라이언트로 전파.
 * 해피오더는 수기 협의(API 없음), KARROT은 외부 시스템이 없어 no-op.
 */
@Component
class PosStoreRegistrar(
    private val foodTechClient: FoodTechClient,
) {
    fun registerStore(store: Store) {
        when (store.partnerType) {
            PartnerType.INTEGRATED_PARTNER -> {
                val context = checkNotNull(store.directPos)
                (context.partner as? StoreRegistrable)?.registerStore(context.partnerStoreCode)
            }
            PartnerType.FOODTECH -> foodTechClient.linkStore(store.id)
            PartnerType.HAPPYORDER, PartnerType.KARROT -> Unit  // 수기 협의 / 외부 없음
        }
    }

    fun unregisterStore(store: Store) {
        when (store.partnerType) {
            PartnerType.INTEGRATED_PARTNER -> {
                val context = checkNotNull(store.directPos)
                (context.partner as? StoreRegistrable)?.unregisterStore(context.partnerStoreCode)
            }
            PartnerType.FOODTECH -> foodTechClient.unlinkStore(store.id)
            PartnerType.HAPPYORDER, PartnerType.KARROT -> Unit
        }
    }
}
