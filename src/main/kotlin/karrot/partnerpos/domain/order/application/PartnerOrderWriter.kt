package karrot.partnerpos.domain.order.application

import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.infra.PartnerOrderEntity
import karrot.partnerpos.infra.PartnerOrderRepository
import karrot.partnerpos.infra.PartnerRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

/**
 * 파트너 주문 매핑 원장(`partner_orders`) 쓰기 (AS-IS 서버 컨벤션: 서비스 → Writer → Repository).
 *
 * 같은 주문의 중복 등록은 `order_code` 유니크 제약이 **최종 방어**하고,
 * uk 위반을 도메인 언어(이미 등록된 주문)로 해석하는 것이 이 레이어의 책임이다 (AS-IS 409 해석).
 * saveAndFlush로 제약 검증을 즉시 트리거해 실패를 호출 지점에서 잡는다.
 */
@Component
class PartnerOrderWriter(
    private val partnerOrderRepository: PartnerOrderRepository,
    private val partnerRepository: PartnerRepository,
) {
    fun write(orderCode: OrderCode, partnerKey: PartnerKey) {
        val partner = partnerRepository.findByPartnerKey(partnerKey.name)
            ?: throw NoSuchElementException("partners row not found for key: ${partnerKey.name}")

        try {
            partnerOrderRepository.saveAndFlush(
                PartnerOrderEntity(orderCode = orderCode.value, partnerId = partner.id),
            )
        } catch (e: DataIntegrityViolationException) {
            throw IllegalStateException("order already registered to partner: ${orderCode.value}", e)
        }
    }
}
