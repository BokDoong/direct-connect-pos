package karrot.partnerpos.infra

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import karrot.partnerpos.domain.store.model.PartnerType
import org.springframework.data.jpa.repository.JpaRepository

/** `stores` — 매장 원장. `partner_type`이 모든 파트너 타입 분기의 시작점 (AS-IS 유지). */
@Entity
@Table(name = "stores")
class StoreEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", nullable = false, length = 30)
    val partnerType: PartnerType,
)

interface StoreRepository : JpaRepository<StoreEntity, Long>
