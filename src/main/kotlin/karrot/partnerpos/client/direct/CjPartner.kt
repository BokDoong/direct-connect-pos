package karrot.partnerpos.client.direct

import karrot.partnerpos.config.DirectPosProperties
import karrot.partnerpos.domain.pos.model.DirectPosPartner
import karrot.partnerpos.domain.menu.model.MenuCode
import karrot.partnerpos.domain.menu.model.MenuStock
import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.pos.model.PartnerKey
import karrot.partnerpos.domain.pos.model.PartnerPolicy
import karrot.partnerpos.domain.order.model.PosOrder
import karrot.partnerpos.domain.pos.model.StockQueryable
import karrot.partnerpos.domain.store.model.StoreCode
import karrot.partnerpos.domain.pos.model.StoreRegistrable
import karrot.partnerpos.client.transport.PosApiTransport
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.seconds

/**
 * CJ푸드빌 (뚜레쥬르) — 직연동 풀세트 파트너.
 *
 * 이 파일이 CJ의 전체 명세다:
 * 재고 조회 지원(StockQueryable), 매장 등록/해지 지원(StoreRegistrable),
 * 미수락 자동취소 600초, 페이로드는 당근 공통 규격 그대로.
 */
@Component
class CjPartner(
    private val transport: PosApiTransport,
    props: DirectPosProperties,
) : DirectPosPartner, StockQueryable, StoreRegistrable {

    override val key = KEY
    override val policy = PartnerPolicy(unacceptedAutoCancel = 600.seconds)
    private val endpoint = props.endpointOf(KEY)

    override fun registerOrder(order: PosOrder) {
        transport.post(endpoint, REGISTER_ORDER_PATH, CommonOrderPayload.from(order))
    }

    override fun cancelOrder(orderCode: OrderCode) {
        transport.post(endpoint, cancelOrderPath(orderCode), OrderCancelPayload(orderCode.value))
    }

    override fun fetchStocks(storeCode: StoreCode, menuCodes: List<MenuCode>): List<MenuStock> =
        transport.postForBody<List<MenuStockResponse>>(
            endpoint,
            STOCKS_PATH,
            StockInquiryPayload(storeCode.value, menuCodes.map { it.value }),
        ).map { MenuStock(MenuCode(it.menuCode), it.quantity) }

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
        val KEY = PartnerKey("CJ_FOODVILLE")

        const val REGISTER_ORDER_PATH = "/api/v1/karrot-pickup/orders/register"
        const val STOCKS_PATH = "/api/v1/karrot-pickup/stocks"
        const val STORE_REGISTRATION_PATH = "/api/v1/karrot-pickup/stores/registration"
        fun cancelOrderPath(orderCode: OrderCode) = "/api/v1/karrot-pickup/orders/${orderCode.value}/cancel"
    }
}
