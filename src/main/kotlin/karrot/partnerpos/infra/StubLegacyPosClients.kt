package karrot.partnerpos.infra

import karrot.partnerpos.client.legacy.FoodTechClient
import karrot.partnerpos.client.legacy.HappyOrderClient
import karrot.partnerpos.domain.menu.model.MenuCode
import karrot.partnerpos.domain.menu.model.MenuStock
import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.order.model.PosOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** 데모용 스텁 — 실환경에서는 AS-IS의 각 클라이언트(FoodTechApiClient 등)가 이 자리에 온다. */
@Component
class StubFoodTechClient : FoodTechClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun registerOrder(order: PosOrder) = log.info("[stub] foodtech 주문 등록: {}", order.orderCode.value)
    override fun cancelOrder(orderCode: OrderCode) = log.info("[stub] foodtech 주문 취소: {}", orderCode.value)
    override fun linkStore(storeId: Long) = log.info("[stub] foodtech 매장 연동: {}", storeId)
    override fun unlinkStore(storeId: Long) = log.info("[stub] foodtech 매장 해지: {}", storeId)
}

@Component
class StubHappyOrderClient : HappyOrderClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun registerOrder(order: PosOrder) = log.info("[stub] happyorder 주문 등록: {}", order.orderCode.value)
    override fun cancelOrder(orderCode: OrderCode) = log.info("[stub] happyorder 주문 취소: {}", orderCode.value)
    override fun fetchStocks(storeId: Long, menuCodes: List<MenuCode>): List<MenuStock> = emptyList()
}
