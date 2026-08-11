package karrot.partnerpos.domain.partner.application

import karrot.partnerpos.domain.partner.model.PartnerKey

/**
 * `partners` row의 사본 — 다이어트된 파트너 마스터.
 * `key`는 TO-BE에서 신설한 컬럼(03 결정 D8): 코드의 [PartnerKey]와 1:1 매핑되는 안정 식별자.
 * `name`(표시·인바운드 URL 검증용 — 변경될 수 있음)과 분리해, 이름 변경이 dispatch를 깨지 않게 한다.
 */
data class PartnerRecord(
    val id: Long,
    val name: String,
    val key: String,
)

interface PartnerRecordRepository {
    fun getById(partnerId: Long): PartnerRecord

    /** 기동 대사(PartnerRegistryReconciler)용. */
    fun findAllKeys(): List<String>
}
