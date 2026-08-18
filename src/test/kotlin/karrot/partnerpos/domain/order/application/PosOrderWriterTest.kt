package karrot.partnerpos.domain.order.application

import karrot.partnerpos.RecordingPartner
import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.foodTechStore
import karrot.partnerpos.happyOrderStore
import karrot.partnerpos.infra.PartnerEntity
import karrot.partnerpos.infra.PartnerOrderRepository
import karrot.partnerpos.infra.PartnerRepository
import karrot.partnerpos.integratedStore
import karrot.partnerpos.karrotStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

/** 주문 매핑 쓰기의 타입 분기 + uk 방어를 실제 H2 위에서 검증한다. */
@DataJpaTest(properties = ["spring.sql.init.mode=never"])
class PosOrderWriterTest {

    @Autowired
    private lateinit var partnerOrderRepository: PartnerOrderRepository

    @Autowired
    private lateinit var partnerRepository: PartnerRepository

    private val writer by lazy { PosOrderWriter(PartnerOrderWriter(partnerOrderRepository, partnerRepository)) }

    @Test
    @DisplayName("직연동 매장만 partner_orders 매핑에 저장된다 — 레거시 매핑은 각자 경로 소관")
    fun onlyIntegratedWritesMapping() {
        val direct = partnerRepository.save(PartnerEntity(name = "직연동 파트너", partnerKey = "DIRECT"))
        val orderCode = OrderCode("2608110000ABCD")

        writer.write(integratedStore(RecordingPartner(key = PartnerKey("DIRECT"))), orderCode)
        writer.write(foodTechStore(), orderCode)
        writer.write(happyOrderStore(), orderCode)
        writer.write(karrotStore(), orderCode)

        assertThat(partnerOrderRepository.findAll())
            .singleElement()
            .satisfies({
                assertThat(it.orderCode).isEqualTo(orderCode.value)
                assertThat(it.partnerId).isEqualTo(direct.id)
            })
    }

    @Test
    @DisplayName("partners row가 없는 키로 쓰면 명확한 예외를 던진다 — 기동 대사가 놓친 drift의 2차 방어")
    fun unknownPartnerKeyThrows() {
        val store = integratedStore(RecordingPartner(key = PartnerKey("GHOST")))

        assertThrows<NoSuchElementException> { writer.write(store, OrderCode("2608110000ABCD")) }
    }

    @Test
    @DisplayName("같은 order_code 중복 쓰기는 uk 위반을 도메인 예외로 해석한다 (AS-IS 409)")
    fun duplicateWriteRejected() {
        partnerRepository.save(PartnerEntity(name = "직연동 파트너", partnerKey = "DIRECT"))
        val store = integratedStore(RecordingPartner(key = PartnerKey("DIRECT")))
        val orderCode = OrderCode("2608110000ABCD")

        writer.write(store, orderCode)

        val thrown = assertThrows<IllegalStateException> { writer.write(store, orderCode) }
        assertThat(thrown.message).contains("already registered")
    }
}
