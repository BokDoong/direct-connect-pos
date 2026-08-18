package karrot.partnerpos.domain.store.application

import karrot.partnerpos.domain.pos.application.DirectPosPartnerRegistry
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.domain.store.model.DirectPosContext
import karrot.partnerpos.domain.store.model.PartnerType
import karrot.partnerpos.domain.store.model.Store
import karrot.partnerpos.domain.store.model.StoreCode
import karrot.partnerpos.infra.PartnerRepository
import karrot.partnerpos.infra.PartnerStoreRepository
import karrot.partnerpos.infra.StoreRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * Entity → Store 도메인 모델 조립의 단일 지점 (AS-IS StoreFinder 컨벤션 재현).
 *
 * INTEGRATED_PARTNER 매장은 partner_stores → partners 조인(A안 — id→key 번역)으로 키를 얻어
 * registry에서 파트너를 resolve한다. **데이터(key)가 행위(전략)로 번역되는 유일한 지점.**
 * registry 의존이 앱 서비스 4곳에서 여기 한 곳으로 응집된다.
 */
@Component
class StoreFinder(
    private val storeRepository: StoreRepository,
    private val partnerStoreRepository: PartnerStoreRepository,
    private val partnerRepository: PartnerRepository,
    private val registry: DirectPosPartnerRegistry,
) {
    fun find(storeId: Long): Store {
        val store = storeRepository.findByIdOrNull(storeId)
            ?: throw NoSuchElementException("store not found: $storeId")
        if (store.partnerType != PartnerType.INTEGRATED_PARTNER) {
            return Store(store.id, store.name, store.partnerType, directPos = null)
        }

        val link = partnerStoreRepository.findByStoreId(storeId)
            ?: throw NoSuchElementException("partner store link not found: $storeId")
        val partner = partnerRepository.findByIdOrNull(link.partnerId)
            ?: throw NoSuchElementException("partner not found: ${link.partnerId}")

        return Store(
            id = store.id,
            name = store.name,
            partnerType = store.partnerType,
            directPos = DirectPosContext(
                partner = registry[PartnerKey(partner.partnerKey)],   // 미등록 키는 여기서 fail-loud
                partnerStoreCode = StoreCode(link.partnerStoreCode),
            ),
        )
    }
}
