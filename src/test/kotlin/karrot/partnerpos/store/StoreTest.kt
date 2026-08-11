package karrot.partnerpos.store

import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.contract.StoreCode
import karrot.partnerpos.integratedStore
import karrot.partnerpos.karrotStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * sealed 대신 enum + nullable 필드를 유지한 대가로 세운 방어선의 검증:
 * 불법 조립은 생성 시점 양방향 불변식이 막는다 — 따라서 null은 그 자체로 "직연동 아님"을 뜻한다.
 */
class StoreTest {

    @Test
    @DisplayName("INTEGRATED_PARTNER인데 파트너 없이 조립하면 생성 시점에 실패한다")
    fun integratedWithoutPartnerFailsAtConstruction() {
        assertThrows<IllegalArgumentException> {
            Store(1L, "매장", PartnerType.INTEGRATED_PARTNER, directPosPartner = null, partnerStoreCode = null)
        }
    }

    @Test
    @DisplayName("직연동이 아닌데 파트너 맥락을 들면 생성 시점에 실패한다 — 불변식은 양방향")
    fun nonIntegratedWithPartnerFailsAtConstruction() {
        assertThrows<IllegalArgumentException> {
            Store(
                1L, "매장", PartnerType.KARROT,
                directPosPartner = RecordingPartner(),
                partnerStoreCode = StoreCode("STORE-001"),
            )
        }
    }

    @Test
    @DisplayName("직연동 매장은 조립된 파트너와 매장 코드를 property로 직접 노출한다")
    fun integratedStoreExposesContext() {
        val partner = RecordingPartner()
        val store = integratedStore(partner, partnerStoreCode = "CJ-STORE-042")

        assertThat(store.directPosPartner).isSameAs(partner)
        assertThat(store.partnerStoreCode).isEqualTo(StoreCode("CJ-STORE-042"))
    }

    @Test
    @DisplayName("직연동이 아닌 매장의 파트너는 null — 불변식 덕분에 null이 곧 '직연동 아님'이다")
    fun nonIntegratedStoreHasNoPartner() {
        assertThat(karrotStore().directPosPartner).isNull()
        assertThat(karrotStore().partnerStoreCode).isNull()
    }
}
