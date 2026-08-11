package karrot.partnerpos.contract

import org.springframework.stereotype.Component

/**
 * 파트너 선택(keyed dispatch)의 단일 지점.
 *
 * Spring이 [DirectPosPartner] 구현 빈 전체를 List로 수집해 주입한다 — 새 파트너 클래스를
 * 추가하면 등록 코드 0줄로 여기에 실린다. 파트너 키는 1:1 닫힌 매핑이므로 List 순회가 아니라
 * Map 색인으로 제공하고, 중복 키는 기동 시점에 fail-fast로 잡는다.
 */
@Component
class DirectPosPartnerRegistry(partners: List<DirectPosPartner>) {

    private val byKey: Map<PartnerKey, DirectPosPartner> =
        partners.associateBy { it.key }.also { indexed ->
            require(indexed.size == partners.size) {
                val duplicated = partners.groupingBy { it.key }.eachCount().filterValues { it > 1 }.keys
                "duplicate PartnerKey registration: $duplicated"
            }
        }

    /** 등록된 전체 키 — 기동 시 코드↔DB 대사(reconciliation)용. */
    val keys: Set<PartnerKey> get() = byKey.keys

    operator fun get(key: PartnerKey): DirectPosPartner =
        byKey[key] ?: throw IllegalStateException("no partner registered for key: $key")
}
