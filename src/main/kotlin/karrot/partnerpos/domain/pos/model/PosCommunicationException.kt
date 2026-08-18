package karrot.partnerpos.domain.pos.model

/** 파트너 통신 실패. 4xx(계약 위반)와 재시도 소진(5xx·타임아웃)을 모두 이 타입으로 수렴한다. */
class PosCommunicationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
