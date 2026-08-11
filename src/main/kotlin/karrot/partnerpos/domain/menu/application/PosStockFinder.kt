package karrot.partnerpos.domain.menu.application

import karrot.partnerpos.domain.menu.model.MenuCode
import karrot.partnerpos.domain.menu.model.MenuStock
import karrot.partnerpos.domain.partner.model.StockQueryable
import karrot.partnerpos.client.legacy.HappyOrderClient
import karrot.partnerpos.domain.store.model.PartnerType
import karrot.partnerpos.domain.store.model.Store
import org.springframework.stereotype.Component

/**
 * 재고 Pull 조회의 파트너 타입 분기 지점 (AS-IS `PartnerStockFinder`/`HappyOrderStockFinder` 재현).
 *
 * 직연동은 capability(StockQueryable 구현 여부)로, 해피오더는 레거시 클라이언트로,
 * 푸드테크(푸시형)·KARROT은 Pull 자체가 없다.
 */
@Component
class PosStockFinder(
    private val happyOrderClient: HappyOrderClient,
) {
    /** @return null = 재고 Pull 미지원(푸시형이거나 미제공) — 호출자는 DB 값을 쓴다. 실패는 예외로 전파. */
    fun findStocks(store: Store, menuCodes: List<MenuCode>): List<MenuStock>? =
        when (store.partnerType) {
            PartnerType.INTEGRATED_PARTNER -> {
                val context = checkNotNull(store.directPos)
                (context.partner as? StockQueryable)?.fetchStocks(context.partnerStoreCode, menuCodes)
            }
            PartnerType.HAPPYORDER -> happyOrderClient.fetchStocks(store.id, menuCodes)
            PartnerType.FOODTECH, PartnerType.KARROT -> null
        }
}
