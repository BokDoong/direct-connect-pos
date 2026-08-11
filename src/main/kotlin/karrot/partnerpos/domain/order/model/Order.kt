package karrot.partnerpos.domain.order.model

import karrot.partnerpos.domain.menu.model.MenuCode
import karrot.partnerpos.domain.store.model.StoreCode

/** 당근이 채번한 주문 코드 — 파트너에 전달하는 유일한 주문 식별자. 16자 (롯데 varchar(20) 제한 대응). */
@JvmInline
value class OrderCode(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 16) { "OrderCode must be 1..16 chars: $value" }
    }
}

/** 당근페이 결제수단. 파트너별 코드 매핑은 각 파트너 구현체 안에서만 일어난다. */
enum class PaymentMethod { KARROT_PAY_MONEY, CARD }

data class PosOrderItem(
    val menuCode: MenuCode,
    val quantity: Int,
    val unitPrice: Long,
)

/** 파트너로 내보내는 주문의 도메인 표현 — 어느 파트너의 용어에도 종속되지 않는다. */
data class PosOrder(
    val orderCode: OrderCode,
    val storeCode: StoreCode,
    val items: List<PosOrderItem>,
    val totalAmount: Long,
    val paymentMethod: PaymentMethod,
)
