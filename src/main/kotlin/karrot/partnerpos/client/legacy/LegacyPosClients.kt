package karrot.partnerpos.client.legacy

import karrot.partnerpos.domain.menu.model.MenuCode
import karrot.partnerpos.domain.menu.model.MenuStock
import karrot.partnerpos.domain.order.model.OrderCode
import karrot.partnerpos.domain.order.model.PosOrder

/**
 * 레거시 연동 클라이언트 포트 — 푸드테크·해피오더는 각자 규격(파트너가 규격 주도)이라
 * 직연동처럼 공통 인터페이스로 통합하지 않고 AS-IS 형태를 유지한다.
 * 실제 구현(3중키 매장 식별, 사전검증 등)은 재설계 스코프 밖 — 여기서는 경계만 재현한다.
 * 데모용 스텁 구현은 infra 패키지에 있다.
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
