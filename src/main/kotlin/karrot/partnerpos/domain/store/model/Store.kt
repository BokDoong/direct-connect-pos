package karrot.partnerpos.domain.store.model

import karrot.partnerpos.domain.partner.model.DirectPosPartner
import karrot.partnerpos.domain.store.model.StoreCode

/** 연동 유형 — AS-IS `stores.partner_type` enum 유지 (재모델링은 범위 밖, 03 결정 D6). */
enum class PartnerType { KARROT, FOODTECH, HAPPYORDER, INTEGRATED_PARTNER }

/**
 * 직연동 매장의 파트너 맥락 — resolve된 파트너(행위)와 파트너측 매장 코드는 항상 함께 다닌다.
 * "둘은 함께 있거나 함께 없다"는 불변식이 non-null 필드로 타입에 박제되어, 호출부의 `!!`가 소멸한다.
 */
data class DirectPosContext(
    val partner: DirectPosPartner,
    val partnerStoreCode: StoreCode,
)

/**
 * 매장 도메인 모델 — StoreFinder가 entity에서 조립해 돌려주는 읽기 모델.
 *
 * sealed 재모델링 대신 기존 enum을 유지하되, 잃는 타입 안전성은 생성 시점 양방향 불변식으로 보완한다 —
 * 조립 지점이 StoreFinder 하나뿐이므로 잘못 조립된 Store는 존재할 수 없고,
 * 따라서 `directPos == null`은 그 자체로 "직연동 매장이 아님"을 뜻한다.
 */
class Store(
    val id: Long,
    val name: String,
    val partnerType: PartnerType,
    val directPos: DirectPosContext?,
) {
    init {
        require((partnerType == PartnerType.INTEGRATED_PARTNER) == (directPos != null)) {
            "INTEGRATED_PARTNER ⇔ directPos context violated (store $id, $partnerType)"
        }
    }
}
