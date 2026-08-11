package karrot.partnerpos.spec

import karrot.partnerpos.contract.PosOrder

/**
 * 당근 공통 규격서의 코드화 — CJ·롯데는 이 페이로드를 그대로 쓰고,
 * 파트너별 확장(버거킹)은 이것을 부품으로 포함해 조립한다.
 */
data class CommonOrderPayload(
    val orderCode: String,
    val storeCode: String,
    val totalAmount: Long,
    val items: List<Item>,
) {
    data class Item(val menuCode: String, val quantity: Int, val unitPrice: Long)

    companion object {
        fun from(order: PosOrder) = CommonOrderPayload(
            orderCode = order.orderCode.value,
            storeCode = order.storeCode.value,
            totalAmount = order.totalAmount,
            items = order.items.map { Item(it.menuCode.value, it.quantity, it.unitPrice) },
        )
    }
}

/** 취소 전파 규격 — 사유는 "가게 사정" 고정 (AS-IS 하드코딩 정책의 명시적 승계). */
data class OrderCancelPayload(
    val orderCode: String,
    val reason: String = FIXED_CANCEL_REASON,
) {
    companion object {
        const val FIXED_CANCEL_REASON = "가게 사정"
    }
}

/** 재고 조회 규격 — 요청: 메뉴코드 리스트. */
data class StockInquiryPayload(
    val storeCode: String,
    val menuCodes: List<String>,
)

/** 재고 조회 규격 — 응답: (메뉴코드+수량) 리스트. */
data class MenuStockResponse(
    val menuCode: String,
    val quantity: Int,
)

/**
 * 매장 등록/해지 겸용 규격. AS-IS는 body에 storeCode를 담았고 겸용 엔드포인트였다 —
 * 구분 필드의 실제 형태는 미상이라 action 필드로 가정한다 (04 문서 §9 가정).
 */
data class StoreRegistrationPayload(
    val storeCode: String,
    val action: Action,
) {
    enum class Action { REGISTER, UNREGISTER }
}
