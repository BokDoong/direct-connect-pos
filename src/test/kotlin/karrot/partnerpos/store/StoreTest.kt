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
    @DisplayName("INTEGRATED_PARTNER인데 파트너 맥락 없이 조립하면 생성 시점에 실패한다")
    fun integratedWithoutContextFailsAtConstruction() {
        assertThrows<IllegalArgumentException> {
            Store(1L, "매장", PartnerType.INTEGRATED_PARTNER, directPos = null)
        }
    }

    @Test
    @DisplayName("직연동이 아닌데 파트너 맥락을 들면 생성 시점에 실패한다 — 불변식은 양방향")
    fun nonIntegratedWithContextFailsAtConstruction() {
        assertThrows<IllegalArgumentException> {
            Store(
                1L, "매장", PartnerType.KARROT,
                directPos = DirectPosContext(RecordingPartner(), StoreCode("STORE-001")),
            )
        }
    }

    @Test
    @DisplayName("직연동 매장의 파트너와 매장 코드는 컨텍스트 안에서 non-null — '둘은 함께'가 타입으로 보장된다")
    fun integratedStoreExposesContext() {
        val partner = RecordingPartner()
        val store = integratedStore(partner, partnerStoreCode = "CJ-STORE-042")

        assertThat(store.directPos!!.partner).isSameAs(partner)
        assertThat(store.directPos!!.partnerStoreCode).isEqualTo(StoreCode("CJ-STORE-042"))
    }

    @Test
    @DisplayName("직연동이 아닌 매장의 컨텍스트는 null — 불변식 덕분에 null이 곧 '직연동 아님'이다")
    fun nonIntegratedStoreHasNoContext() {
        assertThat(karrotStore().directPos).isNull()
    }
}
