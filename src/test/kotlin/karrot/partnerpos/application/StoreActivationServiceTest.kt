package karrot.partnerpos.application

import karrot.partnerpos.RecordingFoodTechClient
import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.contract.PosCommunicationException
import karrot.partnerpos.contract.StoreCode
import karrot.partnerpos.contract.StoreRegistrable
import karrot.partnerpos.integratedStore
import karrot.partnerpos.karrotStore
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

    private val registrable = RegistrablePartner()
    private val service = StoreActivationService(PartnerPosStoreRegistrar(RecordingFoodTechClient()))

    @Test
    @DisplayName("등록 지원 파트너는 파트너측 매장 코드로 등록 성공해야 활성화된다")
    fun registrablePartnerRegistersBeforeActivation() {
        val store = integratedStore(registrable, partnerStoreCode = "CJ-STORE-042")

        val result = service.activate(store)

        assertThat(result).isEqualTo(ActivationResult.Activated)
        assertThat(registrable.registeredStores).containsExactly(StoreCode("CJ-STORE-042"))
    }

    @Test
    @DisplayName("매장 등록 실패 = 활성화 실패 — AS-IS와 동일한 hard 의존")
    fun registrationFailureFailsActivation() {
        registrable.failOnRegisterStore = true

        val result = service.activate(integratedStore(registrable))

        assertThat(result).isInstanceOf(ActivationResult.Failed::class.java)
    }

    @Test
    @DisplayName("등록 미지원 직연동 파트너(롯데형 — 수기 협의)는 즉시 활성화된다")
    fun unsupportedPartnerActivatesImmediately() {
        val store = integratedStore(RecordingPartner(key = PartnerKey("PLAIN_PARTNER")))

        val result = service.activate(store)

        assertThat(result).isEqualTo(ActivationResult.Activated)
    }

    @Test
    @DisplayName("직연동이 아닌 매장은 파트너 왕복 없이 활성화된다")
    fun nonIntegratedStoreActivatesWithoutPartner() {
        val result = service.activate(karrotStore())

        assertThat(result).isEqualTo(ActivationResult.Activated)
    }
}
