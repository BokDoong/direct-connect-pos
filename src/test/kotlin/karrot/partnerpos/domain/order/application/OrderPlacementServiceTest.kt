package karrot.partnerpos.domain.order.application

import karrot.partnerpos.RecordingFoodTechClient
import karrot.partnerpos.RecordingHappyOrderClient
import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.domain.pos.model.PosCommunicationException
import karrot.partnerpos.infra.PartnerEntity
import karrot.partnerpos.infra.PartnerOrderEntity
import karrot.partnerpos.infra.PartnerOrderRepository
import karrot.partnerpos.infra.PartnerRepository
import karrot.partnerpos.integratedStore
import karrot.partnerpos.sampleOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

/** 주문 등록 오케스트레이션 — 매핑 저장 실패(uk 위반)를 실제 H2 제약으로 재현해 보상 흐름을 검증한다. */
@DataJpaTest(properties = ["spring.sql.init.mode=never"])
class OrderPlacementServiceTest {

    @Autowired
    private lateinit var partnerOrderRepository: PartnerOrderRepository

    @Autowired
    private lateinit var partnerRepository: PartnerRepository

    private val partner = RecordingPartner(key = PartnerKey("TEST_PARTNER"))
    private val store = integratedStore(partner)
    private var partnerRowId: Long = 0

    private val service by lazy {
        OrderPlacementService(
            PosOrderWriter(PartnerOrderWriter(partnerOrderRepository, partnerRepository)),
            PosOrderSynchronizer(RecordingFoodTechClient(), RecordingHappyOrderClient()),
        )
    }

    @BeforeEach
    fun seedPartnerRow() {
        partnerRowId = partnerRepository.save(PartnerEntity(name = "테스트 파트너", partnerKey = "TEST_PARTNER")).id
    }

    @Test
    @DisplayName("정상 흐름: 파트너 등록 후 매핑이 저장된다")
    fun placeRegistersAndSaves() {
        val order = sampleOrder()

        service.place(store, order)

        assertThat(partner.registeredOrders).containsExactly(order)
        assertThat(partner.canceledOrderCodes).isEmpty()
        assertThat(partnerOrderRepository.findAll())
            .singleElement()
            .satisfies({ assertThat(it.orderCode).isEqualTo(order.orderCode.value) })
    }

    @Test
    @DisplayName("유령주문 방지: 등록 성공 후 매핑 저장이 실패하면 즉시 취소를 전파하고 예외를 다시 던진다")
    fun ghostOrderCompensation() {
        val order = sampleOrder()
        // 매핑 저장 실패를 uk 위반으로 재현 — 같은 order_code 행을 미리 넣어둔다
        partnerOrderRepository.saveAndFlush(
            PartnerOrderEntity(orderCode = order.orderCode.value, partnerId = partnerRowId),
        )

        assertThrows<IllegalStateException> { service.place(store, order) }

        assertThat(partner.registeredOrders).containsExactly(order)
        assertThat(partner.canceledOrderCodes).containsExactly(order.orderCode) // POS로 취소 전파됨
    }

    @Test
    @DisplayName("보상 취소 전파가 실패해도 원인 예외(저장 실패)가 유지된다 — best-effort")
    fun compensationFailureDoesNotMaskCause() {
        partner.failOnCancel = true
        val order = sampleOrder()
        partnerOrderRepository.saveAndFlush(
            PartnerOrderEntity(orderCode = order.orderCode.value, partnerId = partnerRowId),
        )

        assertThrows<IllegalStateException> { service.place(store, order) }
    }

    @Test
    @DisplayName("파트너 등록 자체가 실패하면 매핑 저장도 취소 전파도 없다 — 결제 보상 후 실패 응답")
    fun registerFailurePropagates() {
        partner.failOnRegister = true

        assertThrows<PosCommunicationException> { service.place(store, sampleOrder()) }

        assertThat(partner.registeredOrders).isEmpty()
        assertThat(partner.canceledOrderCodes).isEmpty()
        assertThat(partnerOrderRepository.findAll()).isEmpty()
    }

    @Test
    @DisplayName("중복 등록은 partner_orders.order_code 유니크 제약이 최종 방어한다")
    fun duplicateRegistrationRejected() {
        val order = sampleOrder()

        service.place(store, order)

        assertThrows<IllegalStateException> { service.place(store, order) }
    }
}
