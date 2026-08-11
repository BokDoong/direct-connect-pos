package karrot.partnerpos.contract

/** 당근이 채번한 주문 코드 — 파트너에 전달하는 유일한 주문 식별자. 16자 (롯데 varchar(20) 제한 대응). */
@JvmInline
value class OrderCode(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 16) { "OrderCode must be 1..16 chars: $value" }
    }
}

@JvmInline
value class StoreCode(val value: String)

@JvmInline
value class MenuCode(val value: String)

/** 재고 조회 응답 단위 — 규격: 메뉴코드 + 수량. */
data class MenuStock(val menuCode: MenuCode, val quantity: Int)

/** 당근페이 결제수단. 파트너별 코드 매핑은 각 파트너 구현체 안에서만 일어난다. */
enum class PaymentMethod { KARROT_PAY_MONEY, CARD }

data class OrderItem(
    val menuCode: MenuCode,
    val quantity: Int,
    val unitPrice: Long,
)

/** 파트너로 내보내는 주문의 도메인 표현 — 어느 파트너의 용어에도 종속되지 않는다. */
data class Order(
    val orderCode: OrderCode,
    val storeCode: StoreCode,
    val items: List<OrderItem>,
    val totalAmount: Long,
    val paymentMethod: PaymentMethod,
)

/** 파트너 통신 실패. 4xx(계약 위반)와 재시도 소진(5xx·타임아웃)을 모두 이 타입으로 수렴한다. */
class PosCommunicationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
