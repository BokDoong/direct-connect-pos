package karrot.partnerpos.contract

import karrot.partnerpos.RecordingPartner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DirectPosPartnerRegistryTest {

    @Test
    @DisplayName("키로 파트너를 찾는다 — keyed dispatch")
    fun dispatchByKey() {
        val cj = RecordingPartner(key = PartnerKey("CJ_FOODVILLE"))
        val lotte = RecordingPartner(key = PartnerKey("LOTTE_GRS"))

        val registry = DirectPosPartnerRegistry(listOf(cj, lotte))

        assertThat(registry[PartnerKey("LOTTE_GRS")]).isSameAs(lotte)
    }

    @Test
    @DisplayName("중복 키 등록은 기동 시점에 fail-fast로 잡힌다")
    fun duplicateKeyFailsFast() {
        val one = RecordingPartner(key = PartnerKey("DUP"))
        val two = RecordingPartner(key = PartnerKey("DUP"))

        val thrown = assertThrows<IllegalArgumentException> {
            DirectPosPartnerRegistry(listOf(one, two))
        }

        assertThat(thrown.message).contains("DUP")
    }

    @Test
    @DisplayName("미등록 키 조회는 명확한 예외를 던진다")
    fun unknownKeyThrows() {
        val registry = DirectPosPartnerRegistry(listOf(RecordingPartner(key = PartnerKey("KNOWN"))))

        val thrown = assertThrows<IllegalStateException> { registry[PartnerKey("UNKNOWN")] }

        assertThat(thrown.message).contains("UNKNOWN")
    }
}
