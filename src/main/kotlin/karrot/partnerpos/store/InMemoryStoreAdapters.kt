package karrot.partnerpos.store

import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * 데모용 인메모리 어댑터 — 실환경에서는 JPA/JDBC 어댑터로 교체된다 (docs/06 §6).
 * partners는 파트너 구현 3종과 일치하게 시드해 기동 대사가 통과한다.
 */
@Repository
class InMemoryPartnerRecordRepository : PartnerRecordRepository {
    private val records = listOf(
        PartnerRecord(id = 1, name = "CJ_FOODVILLE"),
        PartnerRecord(id = 2, name = "LOTTE_GRS"),
        PartnerRecord(id = 3, name = "BURGER_KING"),
    ).associateBy { it.id }

    override fun getById(partnerId: Long): PartnerRecord =
        records[partnerId] ?: throw NoSuchElementException("partner not found: $partnerId")

    override fun findAllNames(): List<String> = records.values.map { it.name }
}

@Repository
class InMemoryStoreRecordRepository : StoreRecordRepository {
    private val records = ConcurrentHashMap<Long, StoreRecord>()

    fun save(record: StoreRecord) {
        records[record.id] = record
    }

    override fun getById(storeId: Long): StoreRecord =
        records[storeId] ?: throw NoSuchElementException("store not found: $storeId")
}

@Repository
class InMemoryPartnerStoreLinkRepository : PartnerStoreLinkRepository {
    private val linksByStoreId = ConcurrentHashMap<Long, PartnerStoreLink>()

    fun save(link: PartnerStoreLink) {
        linksByStoreId[link.storeId] = link
    }

    override fun getByStoreId(storeId: Long): PartnerStoreLink =
        linksByStoreId[storeId] ?: throw NoSuchElementException("partner store link not found: $storeId")
}
