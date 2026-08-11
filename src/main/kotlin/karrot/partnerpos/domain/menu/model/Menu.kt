package karrot.partnerpos.domain.menu.model

@JvmInline
value class MenuCode(val value: String)

/** 재고 조회 응답 단위 — 규격: 메뉴코드 + 수량. */
data class MenuStock(val menuCode: MenuCode, val quantity: Int)
