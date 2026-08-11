package karrot.partnerpos.domain.partner.application

import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.domain.partner.application.DirectPosPartnerRegistry
import karrot.partnerpos.domain.partner.model.PartnerKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

/** 분산 enum(DB partners.name ↔ 코드 PartnerKey)의 drift를 기동 실패로 잡는지 검증. */
class PartnerRegistryReconcilerTest {

    private class StubPartnerRecords(private val keys: List<String>) : PartnerRecordRepository {
        override fun getById(partnerId: Long): PartnerRecord = throw UnsupportedOperationException()
        override fun findAllKeys(): List<String> = keys
    }

    private fun registryOf(vararg names: String) =
        DirectPosPartnerRegistry(names.map { RecordingPartner(key = PartnerKey(it)) })

    @Test
    @DisplayName("코드와 DB가 일치하면 통과한다")
    fun matchedSetsPass() {
        val reconciler = PartnerRegistryReconciler(registryOf("CJ", "LOTTE"), StubPartnerRecords(listOf("CJ", "LOTTE")))

        assertDoesNotThrow { reconciler.reconcile() }
    }

    @Test
    @DisplayName("row는 있는데 구현이 없으면 기동 실패 — 주문이 터질 파트너를 배포 시점에 잡는다")
    fun missingImplementationFails() {
        val reconciler = PartnerRegistryReconciler(registryOf("CJ"), StubPartnerRecords(listOf("CJ", "LOTTE")))

        val thrown = assertThrows<IllegalStateException> { reconciler.reconcile() }

        assertThat(thrown.message).contains("missing implementation").contains("LOTTE")
    }

    @Test
    @DisplayName("구현은 있는데 row가 없으면 기동 실패 — FK를 걸 수 없는 파트너를 배포 시점에 잡는다")
    fun missingRowFails() {
        val reconciler = PartnerRegistryReconciler(registryOf("CJ", "BURGER_KING"), StubPartnerRecords(listOf("CJ")))

        val thrown = assertThrows<IllegalStateException> { reconciler.reconcile() }

        assertThat(thrown.message).contains("missing partners row").contains("BURGER_KING")
    }
}
