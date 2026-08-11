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
 * 불법 조립은 생성 시점에, 잘못된 호출은 접근 시점에 명확히 죽는다.
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
    @DisplayName("직연동 매장은 non-null 접근자로 파트너와 매장 코드를 돌려준다 — 호출부 null 체크 불필요")
    fun integratedStoreExposesContext() {
        val partner = RecordingPartner()
        val store = integratedStore(partner, partnerStoreCode = "CJ-STORE-042")

        assertThat(store.directPosPartner()).isSameAs(partner)
        assertThat(store.partnerStoreCode()).isEqualTo(StoreCode("CJ-STORE-042"))
    }

    @Test
    @DisplayName("직연동이 아닌 매장에 파트너를 요청하면 명확한 메시지로 실패한다")
    fun nonIntegratedStoreRejectsPartnerAccess() {
        val thrown = assertThrows<IllegalStateException> { karrotStore().directPosPartner() }

        assertThat(thrown.message).contains("KARROT")
    }
}
