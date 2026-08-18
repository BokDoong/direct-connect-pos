package karrot.partnerpos.infra

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/**
 * `partner_orders` — 직연동 주문 매핑 원장 (AS-IS 유지).
 * `order_code` uk가 중복 등록 멱등의 **최종 방어선** — 코드로 대체 불가능한 것의 대표 (docs/06 §1).
 * (AS-IS는 orders.id를 참조했으나, 주문 원장(orders)은 현재 스코프 밖이라 order_code를 직접 저장한다)
 */
@Entity
@Table(
    name = "partner_orders",
    uniqueConstraints = [UniqueConstraint(name = "uk_partner_orders_order_code", columnNames = ["order_code"])],
)
class PartnerOrderEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "order_code", nullable = false, length = 16)
    val orderCode: String,

    @Column(name = "partner_id", nullable = false)
    val partnerId: Long,

    @Column(name = "registered_at", nullable = false)
    val registeredAt: Instant = Instant.now(),
)

interface PartnerOrderRepository : JpaRepository<PartnerOrderEntity, Long>
