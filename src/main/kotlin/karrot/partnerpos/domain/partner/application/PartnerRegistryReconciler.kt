package karrot.partnerpos.domain.partner.application

import karrot.partnerpos.domain.partner.application.DirectPosPartnerRegistry
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 기동 시 코드↔DB 대사(reconciliation).
 *
 * 파트너 키는 DB(partners.partner_key)와 코드(PartnerKey 선언) 두 곳에 정의되는 **분산 enum**이다 —
 * 행동을 코드로, 정체성을 DB에 남긴 하이브리드 설계의 필연적 이음새.
 * drift(구현 없는 row / row 없는 구현)를 런타임 주문 실패가 아니라 기동 실패로 잡는다.
 * AS-IS의 런타임 문자열 비교(URL path↔name)가 fail-fast로 승격된 것.
 */
@Component
class PartnerRegistryReconciler(
    private val registry: DirectPosPartnerRegistry,
    private val partnerRecords: PartnerRecordRepository,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) = reconcile()

    fun reconcile() {
        val dbKeys = partnerRecords.findAllKeys().toSet()
        val codeKeys = registry.keys.map { it.name }.toSet()

        val notImplemented = dbKeys - codeKeys   // row는 있는데 구현이 없다 → 주문이 터질 파트너
        val notRegistered = codeKeys - dbKeys    // 구현은 있는데 row가 없다 → FK를 걸 수 없는 파트너
        check(notImplemented.isEmpty() && notRegistered.isEmpty()) {
            "partner code-DB drift — missing implementation: $notImplemented, missing partners row: $notRegistered"
        }
    }
}
