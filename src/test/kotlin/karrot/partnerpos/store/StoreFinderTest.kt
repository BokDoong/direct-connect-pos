package karrot.partnerpos.store

import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.contract.DirectPosPartnerRegistry
import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.contract.StoreCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StoreFinderTest {

    private val cj = RecordingPartner(key = PartnerKey("CJ_FOODVILLE"))
    private val registry = DirectPosPartnerRegistry(listOf(cj))

    private val storeRecords = InMemoryStoreRecordRepository()
    private val links = InMemoryPartnerStoreLinkRepository()
    private val partnerRecords = InMemoryPartnerRecordRepository()  // 1=CJ, 2=LOTTE, 3=BK 시드

    private val finder = StoreFinder(storeRecords, links, partnerRecords, registry)

    @Test
    @DisplayName("A안 경로: stores → partner_stores → partners(id→name) → registry 순으로 파트너를 resolve해 조립한다")
    fun assemblesIntegratedStoreViaJoinPath() {
        storeRecords.save(StoreRecord(id = 10L, name = "뚜레쥬르 역삼점", partnerType = PartnerType.INTEGRATED_PARTNER))
        links.save(PartnerStoreLink(storeId = 10L, partnerId = 1L, partnerStoreCode = "CJ-STORE-042"))

        val store = finder.find(10L)

        assertThat(store.partnerType).isEqualTo(PartnerType.INTEGRATED_PARTNER)
        assertThat(store.directPosPartner()).isSameAs(cj)                       // 데이터→행위 번역 완료
        assertThat(store.partnerStoreCode()).isEqualTo(StoreCode("CJ-STORE-042"))
    }

    @Test
    @DisplayName("직연동이 아닌 매장은 side-table 조회 없이 파트너 맥락 없는 Store로 조립된다")
    fun assemblesNonIntegratedStoreWithoutPartnerContext() {
        storeRecords.save(StoreRecord(id = 20L, name = "당근 자체 매장", partnerType = PartnerType.KARROT))

        val store = finder.find(20L)

        assertThat(store.partnerType).isEqualTo(PartnerType.KARROT)
        assertThrows<IllegalStateException> { store.directPosPartner() }
    }

    @Test
    @DisplayName("DB에는 있는데 registry에 구현이 없는 파트너는 조립 시점에 fail-loud")
    fun unresolvablePartnerFailsLoud() {
        storeRecords.save(StoreRecord(id = 30L, name = "롯데리아 강남점", partnerType = PartnerType.INTEGRATED_PARTNER))
        links.save(PartnerStoreLink(storeId = 30L, partnerId = 2L, partnerStoreCode = "LT-001"))  // LOTTE_GRS — registry에 없음

        val thrown = assertThrows<IllegalStateException> { finder.find(30L) }

        assertThat(thrown.message).contains("LOTTE_GRS")
    }
}
