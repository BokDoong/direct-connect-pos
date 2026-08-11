package karrot.partnerpos.store

import karrot.partnerpos.contract.DirectPosPartner
import karrot.partnerpos.contract.StoreCode

/** 연동 유형 — AS-IS `stores.partner_type` enum 유지 (재모델링은 범위 밖, 03 결정 D6). */
enum class PartnerType { KARROT, FOODTECH, HAPPYORDER, INTEGRATED_PARTNER }

/**
 * 매장 도메인 모델 — StoreFinder가 entity에서 조립해 돌려주는 읽기 모델.
 *
 * INTEGRATED_PARTNER 매장은 조립 시점에 resolve된 파트너(행위)와 파트너측 매장 코드를 함께 든다.
 * sealed 재모델링 대신 기존 enum을 유지하되, 잃는 타입 안전성은 생성 시점 양방향 불변식으로 보완한다 —
 * 조립 지점이 StoreFinder 하나뿐이므로 잘못 조립된 Store는 존재할 수 없고,
 * 따라서 `directPosPartner == null`은 그 자체로 "직연동 매장이 아님"을 뜻한다 (호출부는 property를 직접 사용).
 */
class Store(
    val id: Long,
    val name: String,
    val partnerType: PartnerType,
    val directPosPartner: DirectPosPartner?,
    val partnerStoreCode: StoreCode?,
) {
    init {
        if (partnerType == PartnerType.INTEGRATED_PARTNER) {
            requireNotNull(directPosPartner) { "INTEGRATED_PARTNER store $id must have a resolved partner" }
            requireNotNull(partnerStoreCode) { "INTEGRATED_PARTNER store $id must have a partner store code" }
        } else {
            require(directPosPartner == null && partnerStoreCode == null) {
                "$partnerType store $id must not carry a direct-pos partner context"
            }
        }
    }
}
