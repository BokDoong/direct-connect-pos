package karrot.partnerpos.infra

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.repository.JpaRepository

/**
 * `partner_stores` — 직연동 매장 side-table (AS-IS 유지).
 * (partner_id, partner_store_code) 복합 uk가 대량 입점 시 코드 점유 충돌을 차단한다.
 */
@Entity
@Table(
    name = "partner_stores",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_partner_stores_store_id", columnNames = ["store_id"]),
        UniqueConstraint(
            name = "uk_partner_stores_partner_store_code",
            columnNames = ["partner_id", "partner_store_code"],
        ),
    ],
)
class PartnerStoreEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "store_id", nullable = false)
    val storeId: Long,

    @Column(name = "partner_id", nullable = false)
    val partnerId: Long,

    @Column(name = "partner_store_code", nullable = false, length = 50)
    val partnerStoreCode: String,
)

interface PartnerStoreRepository : JpaRepository<PartnerStoreEntity, Long> {
    fun findByStoreId(storeId: Long): PartnerStoreEntity?
}
