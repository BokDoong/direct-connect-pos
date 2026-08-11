package karrot.partnerpos.application

import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.contract.PosCommunicationException
import karrot.partnerpos.integratedStore
import karrot.partnerpos.sampleOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderPlacementServiceTest {

    private val partner = RecordingPartner(key = PartnerKey("TEST_PARTNER"))
    private val store = integratedStore(partner)

    @Test
    @DisplayName("정상 흐름: 파트너 등록 후 매핑이 저장된다")
    fun placeRegistersAndSaves() {
        val service = OrderPlacementService(InMemoryPartnerOrderMappingRepository())
        val order = sampleOrder()

        service.place(store, order)

        assertThat(partner.registeredOrders).containsExactly(order)
        assertThat(partner.canceledOrderCodes).isEmpty()
    }

    @Test
    @DisplayName("유령주문 방지: 등록 성공 후 매핑 저장이 실패하면 즉시 취소를 전파하고 예외를 다시 던진다")
    fun ghostOrderCompensation() {
        val failingRepository = object : PartnerOrderMappingRepository {
            override fun save(orderCode: OrderCode, partnerKey: PartnerKey) =
                throw IllegalStateException("db down (stub)")
        }
        val service = OrderPlacementService(failingRepository)
        val order = sampleOrder()

        assertThrows<IllegalStateException> { service.place(store, order) }

        assertThat(partner.registeredOrders).containsExactly(order)
        assertThat(partner.canceledOrderCodes).containsExactly(order.orderCode) // POS로 취소 전파됨
    }

    @Test
    @DisplayName("보상 취소 전파가 실패해도 원인 예외(저장 실패)가 유지된다 — best-effort")
    fun compensationFailureDoesNotMaskCause() {
        partner.failOnCancel = true
        val failingRepository = object : PartnerOrderMappingRepository {
            override fun save(orderCode: OrderCode, partnerKey: PartnerKey) =
                throw IllegalStateException("db down (stub)")
        }
        val service = OrderPlacementService(failingRepository)

        assertThrows<IllegalStateException> { service.place(store, sampleOrder()) }
    }

    @Test
    @DisplayName("파트너 등록 자체가 실패하면 매핑 저장도 취소 전파도 없다 — 결제 보상 후 실패 응답")
    fun registerFailurePropagates() {
        partner.failOnRegister = true
        val service = OrderPlacementService(InMemoryPartnerOrderMappingRepository())

        assertThrows<PosCommunicationException> { service.place(store, sampleOrder()) }

        assertThat(partner.registeredOrders).isEmpty()
        assertThat(partner.canceledOrderCodes).isEmpty()
    }

    @Test
    @DisplayName("중복 등록은 매핑 저장소의 유니크 제약이 방어한다 (AS-IS partner_orders.order_id uk)")
    fun duplicateRegistrationRejected() {
        val service = OrderPlacementService(InMemoryPartnerOrderMappingRepository())
        val order = sampleOrder()

        service.place(store, order)

        assertThrows<IllegalStateException> { service.place(store, order) }
    }
}
