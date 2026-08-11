package karrot.partnerpos.application

import karrot.partnerpos.contract.MenuCode
import karrot.partnerpos.contract.MenuStock
import karrot.partnerpos.contract.PosCommunicationException
import karrot.partnerpos.contract.StockQueryable
import karrot.partnerpos.store.Store
import org.springframework.stereotype.Service

/**
 * 구매 여정의 재고 확인 지점과 실패 정책.
 * soft/hard는 파트너가 아니라 구매 단계의 속성이다 — 그래서 파트너 계층이 아닌 여기(호출자)에 있다.
 */
enum class PurchaseStage(val failureMode: StockFailureMode) {
    MENU_VIEW(StockFailureMode.FALLBACK_TO_DB),   // 메뉴판/카테고리 — soft
    MENU_DETAIL(StockFailureMode.FALLBACK_TO_DB), // 메뉴 상세 — soft
    CART_ADD(StockFailureMode.BLOCK),             // 장바구니 담기 — hard
    CART_VIEW(StockFailureMode.CLOSE_MENUS),      // 장바구니 조회 — 해당 메뉴 품절 처리
    ORDER_CREATE(StockFailureMode.BLOCK),         // 주문 생성 — hard
    PAYMENT(StockFailureMode.BLOCK),              // 결제 승인 직전 — hard
}

enum class StockFailureMode { FALLBACK_TO_DB, BLOCK, CLOSE_MENUS }

sealed interface StockOverlayResult {
    /** 파트너 실시간 재고로 오버레이 성공. */
    data class Overlaid(val stocks: List<MenuStock>) : StockOverlayResult

    /** 재고 미지원 파트너이거나 soft 실패 — DB 값 그대로 사용. */
    data object FromDb : StockOverlayResult

    /** hard 실패 — 담기/주문/결제 차단. */
    data object Blocked : StockOverlayResult

    /** 장바구니 조회 실패 — 대상 메뉴를 품절 처리. */
    data class MenusClosed(val menuCodes: List<MenuCode>) : StockOverlayResult
}

/**
 * 실시간 재고 오버레이 — AS-IS 6개 호출 지점의 공통 진입점.
 *
 * 파트너가 재고를 지원하는지는 boolean 컬럼 조회가 아니라 타입 검사다:
 * `StockQueryable`을 구현하지 않은 파트너에는 fetchStocks가 존재하지 않는다.
 */
@Service
class StockOverlayService {
    fun overlay(
        store: Store,
        menuCodes: List<MenuCode>,
        stage: PurchaseStage,
    ): StockOverlayResult {
        val context = store.directPos ?: return StockOverlayResult.FromDb  // 직연동 아님
        val partner = context.partner
        if (partner !is StockQueryable) return StockOverlayResult.FromDb   // 재고 미지원 파트너

        return try {
            val stocks = partner.fetchStocks(context.partnerStoreCode, menuCodes)
            publishStockSnapshot(stocks)
            StockOverlayResult.Overlaid(stocks)
        } catch (e: PosCommunicationException) {
            when (stage.failureMode) {
                StockFailureMode.FALLBACK_TO_DB -> StockOverlayResult.FromDb
                StockFailureMode.BLOCK -> StockOverlayResult.Blocked
                StockFailureMode.CLOSE_MENUS -> StockOverlayResult.MenusClosed(menuCodes)
            }
        }
    }

    /** 조회 수량의 DB 반영은 비동기 (AS-IS 확인) — 이벤트 발행 위치만 표시하는 의사코드. */
    @Suppress("UNUSED_PARAMETER")
    private fun publishStockSnapshot(stocks: List<MenuStock>) {
        // pseudocode: eventPublisher.publishEvent(StockSnapshotFetched(stocks)) → 비동기 리스너가 DB 반영
    }
}
