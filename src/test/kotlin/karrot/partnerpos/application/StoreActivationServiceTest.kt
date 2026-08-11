package karrot.partnerpos.application

import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.contract.DirectPosPartnerRegistry
import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.contract.PosCommunicationException
import karrot.partnerpos.contract.StoreCode
import karrot.partnerpos.contract.StoreRegistrable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StoreActivationServiceTest {

    private class RegistrablePartner(
        var failOnRegisterStore: Boolean = false,
    ) : RecordingPartner(key = PartnerKey("REGISTRABLE_PARTNER")), StoreRegistrable {
        val registeredStores = mutableListOf<StoreCode>()

        override fun registerStore(storeCode: StoreCode) {
            if (failOnRegisterStore) throw PosCommunicationException("store registration failed (stub)")
            registeredStores += storeCode
        }

        override fun unregisterStore(storeCode: StoreCode) = Unit
    }

    private val plain = RecordingPartner(key = PartnerKey("PLAIN_PARTNER"))
    private val registrable = RegistrablePartner()
    private val service = StoreActivationService(DirectPosPartnerRegistry(listOf(plain, registrable)))
    private val storeCode = StoreCode("STORE-001")

    @Test
    @DisplayName("등록 지원 파트너는 파트너측 매장 등록 성공이 활성화의 전제다")
    fun registrablePartnerRegistersBeforeActivation() {
        val result = service.activate(registrable.key, storeCode)

        assertThat(result).isEqualTo(ActivationResult.Activated)
        assertThat(registrable.registeredStores).containsExactly(storeCode)
    }

    @Test
    @DisplayName("매장 등록 실패 = 활성화 실패 — AS-IS와 동일한 hard 의존")
    fun registrationFailureFailsActivation() {
        registrable.failOnRegisterStore = true

        val result = service.activate(registrable.key, storeCode)

        assertThat(result).isInstanceOf(ActivationResult.Failed::class.java)
    }

    @Test
    @DisplayName("등록 미지원 파트너(롯데형 — 수기 협의)는 즉시 활성화된다")
    fun unsupportedPartnerActivatesImmediately() {
        val result = service.activate(plain.key, storeCode)

        assertThat(result).isEqualTo(ActivationResult.Activated)
    }
}
