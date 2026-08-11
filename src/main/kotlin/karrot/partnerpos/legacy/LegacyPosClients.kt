package karrot.partnerpos.legacy

import karrot.partnerpos.contract.MenuCode
import karrot.partnerpos.contract.MenuStock
import karrot.partnerpos.contract.OrderCode
import karrot.partnerpos.contract.PosOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 레거시 연동 클라이언트 포트 — 푸드테크·해피오더는 각자 규격(파트너가 규격 주도)이라
 * 직연동처럼 공통 인터페이스로 통합하지 않고 AS-IS 형태를 유지한다.
 * 실제 구현(3중키 매장 식별, 사전검증 등)은 재설계 스코프 밖 — 여기서는 경계만 재현한다.
 */
interface FoodTechClient {
    fun registerOrder(order: PosOrder)
    fun cancelOrder(orderCode: OrderCode)

    /** 매장 연동 등록/해지 — AS-IS `/order/channel/store`. */
    fun linkStore(storeId: Long)
    fun unlinkStore(storeId: Long)
    // 재고는 푸시형(품절 신호 인바운드) — Pull API 없음
}

interface HappyOrderClient {
    fun registerOrder(order: PosOrder)   // AS-IS: 전달 전 사전검증 포함
    fun cancelOrder(orderCode: OrderCode)
    fun fetchStocks(storeId: Long, menuCodes: List<MenuCode>): List<MenuStock>
    // 매장 등록은 수기 협의 — API 없음
}

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
