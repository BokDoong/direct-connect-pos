package karrot.partnerpos.domain.store.application

import karrot.partnerpos.domain.partner.application.DirectPosPartnerRegistry
import karrot.partnerpos.domain.partner.model.PartnerKey
import karrot.partnerpos.domain.partner.application.PartnerRecordRepository
import karrot.partnerpos.domain.store.model.DirectPosContext
import karrot.partnerpos.domain.store.model.PartnerType
import karrot.partnerpos.domain.store.model.Store
import karrot.partnerpos.domain.store.model.StoreCode
import org.springframework.stereotype.Component

/** `stores` row의 사본 (영속 표현 — 행위 없음). */
data class StoreRecord(
    val id: Long,
    val name: String,
    val partnerType: PartnerType,
)

/** `partner_stores` row의 사본 — 매장↔파트너 연결과 파트너측 매장 코드. */
data class PartnerStoreLink(
    val storeId: Long,
    val partnerId: Long,
    val partnerStoreCode: String,
)

interface StoreRecordRepository {
    fun getById(storeId: Long): StoreRecord
}

interface PartnerStoreLinkRepository {
    fun getByStoreId(storeId: Long): PartnerStoreLink
}

/**
 * Entity → Store 도메인 모델 조립의 단일 지점 (AS-IS StoreFinder 컨벤션 재현).
 *
 * INTEGRATED_PARTNER 매장은 partner_stores → partners 조인(A안 — id→name 번역)으로 키를 얻어
 * registry에서 파트너를 resolve한다. **데이터(key)가 행위(전략)로 번역되는 유일한 지점.**
 * registry 의존이 앱 서비스 4곳에서 여기 한 곳으로 응집된다.
 */
@Component
class StoreFinder(
    private val storeRecords: StoreRecordRepository,
    private val partnerStoreLinks: PartnerStoreLinkRepository,
    private val partnerRecords: PartnerRecordRepository,
    private val registry: DirectPosPartnerRegistry,
) {
    fun find(storeId: Long): Store {
        val record = storeRecords.getById(storeId)
        if (record.partnerType != PartnerType.INTEGRATED_PARTNER) {
            return Store(record.id, record.name, record.partnerType, directPos = null)
        }

        val link = partnerStoreLinks.getByStoreId(storeId)
        val partnerKey = partnerRecords.getById(link.partnerId).key    // partners: id→key 번역 (A안)
        return Store(
            id = record.id,
            name = record.name,
            partnerType = record.partnerType,
            directPos = DirectPosContext(
                partner = registry[PartnerKey(partnerKey)],             // 미등록 키는 여기서 fail-loud
                partnerStoreCode = StoreCode(link.partnerStoreCode),
            ),
        )
    }
}
