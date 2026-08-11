package karrot.partnerpos.store

import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * 데모용 인메모리 어댑터 — 실환경에서는 JPA/JDBC 어댑터로 교체된다 (docs/06 §6).
 * partners는 파트너 구현 3종과 일치하게 시드해 기동 대사가 통과한다.
 */
@Repository
class InMemoryPartnerRecordRepository : PartnerRecordRepository {
    // name(표시·인바운드용 — 변할 수 있음)과 key(dispatch용 안정 식별자)의 분리를 시드 데이터가 보여준다
    private val records = listOf(
        PartnerRecord(id = 1, name = "CJ푸드빌", key = "CJ_FOODVILLE"),
        PartnerRecord(id = 2, name = "롯데GRS", key = "LOTTE_GRS"),
        PartnerRecord(id = 3, name = "버거킹", key = "BURGER_KING"),
    ).associateBy { it.id }

    override fun getById(partnerId: Long): PartnerRecord =
        records[partnerId] ?: throw NoSuchElementException("partner not found: $partnerId")

    override fun findAllKeys(): List<String> = records.values.map { it.key }
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
