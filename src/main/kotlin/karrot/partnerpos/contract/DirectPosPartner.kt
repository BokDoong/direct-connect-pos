package karrot.partnerpos.contract

import kotlin.time.Duration

/**
 * 직연동 파트너의 필수 계약 — 전 파트너가 반드시 수행하는 것만 선언한다.
 *
 * 반환 규약: 성공 시 정상 반환, 실패 시 [PosCommunicationException].
 * 응답 body는 계약에 없다 — AS-IS에서 HTTP status만 판정하고 body를 활용하지 않았음을 반영.
 */
interface DirectPosPartner {
    val key: PartnerKey
    val policy: PartnerPolicy
    fun registerOrder(order: PosOrder)
    fun cancelOrder(orderCode: OrderCode)
}

/**
 * 재고 조회 capability — `partners.supports_stock` boolean 컬럼의 후계자.
 * 지원 파트너만 구현한다. 미지원 파트너에는 이 메서드 자체가 존재하지 않으므로,
 * 게이트 누락이 런타임 버그가 아니라 컴파일 에러가 된다.
 */
interface StockQueryable {
    fun fetchStocks(storeCode: StoreCode, menuCodes: List<MenuCode>): List<MenuStock>
}

/** 매장 등록/해지 capability — `partners.supports_store_registration` 컬럼의 후계자. */
interface StoreRegistrable {
    fun registerStore(storeCode: StoreCode)
    fun unregisterStore(storeCode: StoreCode)
}

/**
 * 파트너와의 계약값 — `partners.order_delayed_accept_seconds` 컬럼의 승격.
 * 코드에 두는 이유: 계약값 변경은 코드 리뷰와 git 이력을 거쳐야 한다 (AS-IS 문제 P5).
 */
data class PartnerPolicy(
    /** 미수락 자동취소 대기 시간. 결제 완료 시 지연 취소 타이머의 delay로 쓰인다. */
    val unacceptedAutoCancel: Duration,
)
