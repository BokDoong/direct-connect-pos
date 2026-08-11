package karrot.partnerpos.partner

import com.fasterxml.jackson.annotation.JsonUnwrapped
import karrot.partnerpos.config.DirectPosProperties
import karrot.partnerpos.contract.DirectPosPartner
import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.contract.PartnerKey
import karrot.partnerpos.contract.PartnerPolicy
import karrot.partnerpos.contract.PaymentMethod
import karrot.partnerpos.contract.PosOrder
import karrot.partnerpos.contract.StoreCode
import karrot.partnerpos.contract.StoreRegistrable
import karrot.partnerpos.spec.CommonOrderPayload
import karrot.partnerpos.spec.OrderCancelPayload
import karrot.partnerpos.spec.StoreRegistrationPayload
import karrot.partnerpos.transport.PosApiTransport
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.seconds

/**
 * 버거킹 (차기 과제) — 페이로드 확장의 실증.
 *
 * AS-IS의 임계점이었던 요구: 주문 등록 페이로드에 결제수단 필드 추가.
 * TO-BE에서는 공통 규격을 부품으로 포함하고 필드 하나를 더한 전용 페이로드를
 * 이 파일 안에서 조립한다 — 버거킹 용어(결제수단 코드)는 이 파일 밖으로 새지 않는다.
 *
 * capability: 재고 조회 미지원 / 매장 등록 지원. 자동취소 600초(가정 — 계약 확정 시 이 값만 수정).
 */
@Component
class BurgerKingPartner(
    private val transport: PosApiTransport,
    props: DirectPosProperties,
) : DirectPosPartner, StoreRegistrable {

    override val key = KEY
    override val policy = PartnerPolicy(unacceptedAutoCancel = 600.seconds)
    private val endpoint = props.endpointOf(KEY)

    override fun registerOrder(order: PosOrder) {
        transport.post(
            endpoint, REGISTER_ORDER_PATH,
            BurgerKingOrderPayload(
                common = CommonOrderPayload.from(order),
                paymentMethod = order.paymentMethod.toBurgerKingCode(),
            ),
        )
    }

    override fun cancelOrder(orderCode: OrderCode) {
        transport.post(endpoint, cancelOrderPath(orderCode), OrderCancelPayload(orderCode.value))
    }

    override fun registerStore(storeCode: StoreCode) {
        transport.post(
            endpoint, STORE_REGISTRATION_PATH,
            StoreRegistrationPayload(storeCode.value, StoreRegistrationPayload.Action.REGISTER),
        )
    }

    override fun unregisterStore(storeCode: StoreCode) {
        transport.post(
            endpoint, STORE_REGISTRATION_PATH,
            StoreRegistrationPayload(storeCode.value, StoreRegistrationPayload.Action.UNREGISTER),
        )
    }

    companion object {
        val KEY = PartnerKey("BURGER_KING")

        const val REGISTER_ORDER_PATH = "/api/v1/karrot-pickup/orders/register"
        const val STORE_REGISTRATION_PATH = "/api/v1/karrot-pickup/stores/registration"

        fun cancelOrderPath(orderCode: OrderCode) = "/api/v1/karrot-pickup/orders/${orderCode.value}/cancel"
    }
}

/** 버거킹 전용 주문 등록 페이로드 — 공통 규격 + 결제수단. */
data class BurgerKingOrderPayload(
    @field:JsonUnwrapped
    val common: CommonOrderPayload,
    val paymentMethod: String,
)

/** 당근페이 결제수단 → 버거킹 코드 매핑. 이 함수가 이 파일에 있는 것 자체가 경계다. */
private fun PaymentMethod.toBurgerKingCode(): String = when (this) {
    PaymentMethod.KARROT_PAY_MONEY -> "MONEY"
    PaymentMethod.CARD -> "CARD"
}
