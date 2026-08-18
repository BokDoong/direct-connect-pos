package karrot.partnerpos.infra

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * `partners` — 다이어트된 파트너 마스터 (docs/06 §1).
 * AS-IS의 base_url·auth_key·supports_*·정책값 컬럼은 코드/yml로 빠져나갔고,
 * 정체성(name + partner_key)과 FK 앵커 역할만 남는다.
 *
 * `partner_key`는 코드의 PartnerKey와 1:1 매핑되는 안정 식별자 —
 * name(표시용, 변경 가능)과 분리해 이름 변경이 dispatch를 깨지 않게 한다.
 */
@Entity
@Table(
    name = "partners",
    uniqueConstraints = [UniqueConstraint(name = "uk_partners_partner_key", columnNames = ["partner_key"])],
)
class PartnerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    val name: String,

    @Column(name = "partner_key", nullable = false, length = 50)
    val partnerKey: String,
)

interface PartnerRepository : JpaRepository<PartnerEntity, Long> {
    fun findByPartnerKey(partnerKey: String): PartnerEntity?

    /** 기동 대사(PartnerRegistryReconciler)용 — 코드↔DB drift 검출. */
    @Query("select p.partnerKey from PartnerEntity p")
    fun findAllKeys(): List<String>
}
