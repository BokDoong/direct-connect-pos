package karrot.partnerpos.domain.order.application

import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.partner.model.PartnerKey
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * 파트너 주문 매핑 원장 (AS-IS `partner_orders`).
 * 같은 주문의 중복 등록은 저장소의 유니크 제약이 최종 방어한다 (AS-IS order_id uk → 409 해석).
 */
interface PartnerOrderMappingRepository {
    fun save(orderCode: OrderCode, partnerKey: PartnerKey)
}

/**
 * 파트너 주문 매핑 쓰기 구현체 (AS-IS 서버 컨벤션: 서비스 → Writer → Repository).
 * 서비스는 리포지토리를 직접 참조하지 않는다 — 쓰기 정책(트랜잭션 경계, uk 위반의 도메인 해석 등)이
 * 자랄 자리를 계층으로 확보해 두는 것.
 */
@Component
class PartnerOrderWriter(
    private val partnerOrderMappingRepository: PartnerOrderMappingRepository,
) {
    fun write(orderCode: OrderCode, partnerKey: PartnerKey) {
        partnerOrderMappingRepository.save(orderCode, partnerKey)
    }
}
