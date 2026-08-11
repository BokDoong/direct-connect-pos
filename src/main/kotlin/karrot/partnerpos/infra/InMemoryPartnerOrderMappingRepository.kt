package karrot.partnerpos.infra

import karrot.partnerpos.domain.order.application.PartnerOrderMappingRepository
import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.partner.model.PartnerKey
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/** 데모용 인메모리 어댑터 — 실환경에서는 `partner_orders` JPA/JDBC 어댑터로 교체된다 (docs/06 §6). */
@Repository
class InMemoryPartnerOrderMappingRepository : PartnerOrderMappingRepository {
    private val mappings = ConcurrentHashMap<OrderCode, PartnerKey>()

    override fun save(orderCode: OrderCode, partnerKey: PartnerKey) {
        val previous = mappings.putIfAbsent(orderCode, partnerKey)
        check(previous == null) { "order already registered to partner: ${orderCode.value}" }
    }
}
