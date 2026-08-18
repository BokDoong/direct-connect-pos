package karrot.partnerpos.domain.pos.application

import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.infra.PartnerEntity
import karrot.partnerpos.infra.PartnerRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

/** 분산 enum(DB partners.partner_key ↔ 코드 PartnerKey)의 drift를 기동 실패로 잡는지 실제 H2 위에서 검증. */
@DataJpaTest(properties = ["spring.sql.init.mode=never"])
class PartnerRegistryReconcilerTest {

    @Autowired
    private lateinit var partnerRepository: PartnerRepository

    private fun registryOf(vararg names: String) =
        DirectPosPartnerRegistry(names.map { RecordingPartner(key = PartnerKey(it)) })

    private fun seedPartnerRows(vararg keys: String) {
        keys.forEach { partnerRepository.save(PartnerEntity(name = it, partnerKey = it)) }
    }

    @Test
    @DisplayName("코드와 DB가 일치하면 통과한다")
    fun matchedSetsPass() {
        seedPartnerRows("CJ", "LOTTE")
        val reconciler = PartnerRegistryReconciler(registryOf("CJ", "LOTTE"), partnerRepository)

        assertDoesNotThrow { reconciler.reconcile() }
    }

    @Test
    @DisplayName("row는 있는데 구현이 없으면 기동 실패 — 주문이 터질 파트너를 배포 시점에 잡는다")
    fun missingImplementationFails() {
        seedPartnerRows("CJ", "LOTTE")
        val reconciler = PartnerRegistryReconciler(registryOf("CJ"), partnerRepository)

        val thrown = assertThrows<IllegalStateException> { reconciler.reconcile() }

        assertThat(thrown.message).contains("missing implementation").contains("LOTTE")
    }

    @Test
    @DisplayName("구현은 있는데 row가 없으면 기동 실패 — FK를 걸 수 없는 파트너를 배포 시점에 잡는다")
    fun missingRowFails() {
        seedPartnerRows("CJ")
        val reconciler = PartnerRegistryReconciler(registryOf("CJ", "BURGER_KING"), partnerRepository)

        val thrown = assertThrows<IllegalStateException> { reconciler.reconcile() }

        assertThat(thrown.message).contains("missing partners row").contains("BURGER_KING")
    }
}
