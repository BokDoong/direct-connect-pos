package karrot.partnerpos.domain.store.application

import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.domain.pos.application.DirectPosPartnerRegistry
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.domain.store.model.PartnerType
import karrot.partnerpos.domain.store.model.StoreCode
import karrot.partnerpos.infra.PartnerEntity
import karrot.partnerpos.infra.PartnerRepository
import karrot.partnerpos.infra.PartnerStoreEntity
import karrot.partnerpos.infra.PartnerStoreRepository
import karrot.partnerpos.infra.StoreEntity
import karrot.partnerpos.infra.StoreRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

/** 실제 H2 위에서 entity → Store 도메인 모델 조립 경로를 검증한다. */
@DataJpaTest(properties = ["spring.sql.init.mode=never"])
class StoreFinderTest {

    @Autowired
    private lateinit var storeRepository: StoreRepository

    @Autowired
    private lateinit var partnerStoreRepository: PartnerStoreRepository

    @Autowired
    private lateinit var partnerRepository: PartnerRepository

    private val cj = RecordingPartner(key = PartnerKey("CJ_FOODVILLE"))
    private val registry = DirectPosPartnerRegistry(listOf(cj))

    private val finder by lazy { StoreFinder(storeRepository, partnerStoreRepository, partnerRepository, registry) }

    @Test
    @DisplayName("A안 경로: stores → partner_stores → partners(id→key) → registry 순으로 파트너를 resolve해 조립한다")
    fun assemblesIntegratedStoreViaJoinPath() {
        val partner = partnerRepository.save(PartnerEntity(name = "CJ푸드빌", partnerKey = "CJ_FOODVILLE"))
        val store = storeRepository.save(StoreEntity(name = "뚜레쥬르 역삼점", partnerType = PartnerType.INTEGRATED_PARTNER))
        partnerStoreRepository.save(
            PartnerStoreEntity(storeId = store.id, partnerId = partner.id, partnerStoreCode = "CJ-STORE-042"),
        )

        val found = finder.find(store.id)

        assertThat(found.partnerType).isEqualTo(PartnerType.INTEGRATED_PARTNER)
        assertThat(found.directPos!!.partner).isSameAs(cj)                     // 데이터→행위 번역 완료
        assertThat(found.directPos!!.partnerStoreCode).isEqualTo(StoreCode("CJ-STORE-042"))
    }

    @Test
    @DisplayName("직연동이 아닌 매장은 side-table 조회 없이 파트너 맥락 없는 Store로 조립된다")
    fun assemblesNonIntegratedStoreWithoutPartnerContext() {
        val store = storeRepository.save(StoreEntity(name = "당근 자체 매장", partnerType = PartnerType.KARROT))

        val found = finder.find(store.id)

        assertThat(found.partnerType).isEqualTo(PartnerType.KARROT)
        assertThat(found.directPos).isNull()
    }

    @Test
    @DisplayName("DB에는 있는데 registry에 구현이 없는 파트너는 조립 시점에 fail-loud")
    fun unresolvablePartnerFailsLoud() {
        val lotte = partnerRepository.save(PartnerEntity(name = "롯데GRS", partnerKey = "LOTTE_GRS")) // registry에 없음
        val store = storeRepository.save(StoreEntity(name = "롯데리아 강남점", partnerType = PartnerType.INTEGRATED_PARTNER))
        partnerStoreRepository.save(
            PartnerStoreEntity(storeId = store.id, partnerId = lotte.id, partnerStoreCode = "LT-001"),
        )

        val thrown = assertThrows<IllegalStateException> { finder.find(store.id) }

        assertThat(thrown.message).contains("LOTTE_GRS")
    }

    @Test
    @DisplayName("없는 매장 조회는 명확한 예외를 던진다")
    fun missingStoreThrows() {
        assertThrows<NoSuchElementException> { finder.find(404L) }
    }
}
