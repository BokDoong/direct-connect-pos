package karrot.partnerpos.contract

/**
 * 직연동 파트너 식별자.
 *
 * enum이 아니라 value class인 이유: enum이면 새 파트너 추가 시 공유 enum 파일을 수정해야 해서
 * "새 파트너 추가 = 구현 1파일 + 설정"이라는 확장 계약이 깨진다. AS-IS에서도 파트너 식별은
 * `partners.name` 문자열이었다 — 열린 집합이 도메인 사실에 부합한다.
 * 잘 알려진 파트너 키는 각 구현체가 자신의 파일에서 선언한다.
 */
@JvmInline
value class PartnerKey(val name: String) {
    init {
        require(name.isNotBlank()) { "PartnerKey must not be blank" }
    }

    override fun toString(): String = name
}
