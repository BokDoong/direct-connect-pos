# 직연동 POS 아웃바운드 설계

주문 서비스의 **직연동 POS 연동(CJ푸드빌·롯데GRS·버거킹)** 아웃바운드 계층을, "파트너별 차이를 DB row로 파라미터화"하던 기존 구조에서 **전략 + capability 인터페이스 + 컴포지션** 구조로 재설계한 프로젝트입니다.

> **한 줄 요약**: 기존 설계(row INSERT 확장)는 롯데 온보딩을 코드 변경 0으로 끝낼 만큼 유효했지만,
> 버거킹의 "주문 등록 페이로드에 결제수단 추가" 요구처럼 **파트너별 행동 차이**가 등장하는 순간
> 표현 수단이 없다. 이 프로젝트는 그 임계점에서 변하는 축을 다시 식별해 경계를 옮긴 기록이다.

## 문서 읽는 순서

| # | 문서 | 내용 |
|---|---|---|
| 1 | [docs/01-as-is-integrated-partner.md](docs/01-as-is-integrated-partner.md) | AS-IS 구조 — enum+DB row 분기, 스키마, API, "DB에 있던 설정" 인벤토리 |
| 2 | [docs/02-as-is-outbound-flows.md](docs/02-as-is-outbound-flows.md) | AS-IS 아웃바운드 4종의 비즈니스 플로우와 실패 모드 — 추상화 경계(seam)의 발견 |
| 3 | [docs/03-to-be-design-discussion.md](docs/03-to-be-design-discussion.md) | 문제 정의(P1~P7), 설계 결정(D1~D5)과 근거, 결정 로그 |
| 4 | [docs/04-to-be-architecture.md](docs/04-to-be-architecture.md) | TO-BE 아키텍처 — 변하는 축 분석, 컴포넌트 구조도, 상호작용, 확장 시나리오 검증 |
| 5 | [docs/05-extension-guide.md](docs/05-extension-guide.md) | 확장 가이드 — 시나리오별 추가 지점, 사용한 패턴, 무엇이 수월해졌는가 |
| 6 | [docs/06-db-integration.md](docs/06-db-integration.md) | DB 연동 — 테이블별 운명, 코드↔DB 기동 대사, 트랜잭션 경계, 마이그레이션 경로 |

## 패키지 구조

```
src/main/kotlin/karrot/partnerpos/
├── domain/                도메인별로 나누고, 각 도메인 안을 model / application으로 구분
│   ├── order/             주문 — model(PosOrder·OrderCode·PaymentMethod)
│   │                      application(OrderPlacementService·OrderCancelPropagator,
│   │                                  타입 분기 PosOrderSynchronizer·PosOrderWriter, PartnerOrderWriter)
│   ├── menu/              메뉴·재고 — model(MenuCode·MenuStock)
│   │                      application(StockOverlayService — soft/hard 정책, 타입 분기 PosStockFinder)
│   ├── store/             매장 — model(Store·DirectPosContext·PartnerType·StoreCode)
│   │                      application(StoreFinder — 파트너 resolve 유일 지점,
│   │                                  StoreActivationService, 타입 분기 PosStoreRegistrar)
│   └── partner/           파트너 — model(PartnerKey·DirectPosPartner 계약·capability 2종·PartnerPolicy)
│                          application(DirectPosPartnerRegistry, PartnerRegistryReconciler 기동 대사)
├── client/                외부 연동 구현
│   ├── transport/         변하지 않는 전송 규약 1벌 (Bearer·3s/10s·재시도 4회·status 판정)
│   ├── direct/            직연동 구현 3사(CJ·롯데·버거킹) + 공통 규격 페이로드 — 당근 주도 규격
│   └── legacy/            푸드테크·해피오더 클라이언트 포트 — 파트너 주도 규격 (경계만 재현)
├── infra/                 인메모리 리포지토리·레거시 스텁 — domain의 포트에 꽂히는 어댑터
└── config/                환경 데이터·시크릿 바인딩(yml)·RestClient 구성 — 코드가 아닌 유일한 것
```

**패키지 규칙**
- `domain`은 포트(리포지토리 인터페이스)까지만 알고, 구현은 `infra`가 꽂는다 — DB 교체는 infra만 바뀐다
- `*Finder`·`*Writer`·`*Registrar`·`*Synchronizer` 컴포넌트는 소속 도메인의 `application`에 둔다
- 파트너 타입 분기(`Pos*`)와 직연동 전용(`Partner*`)의 접두사 구분은 패키지와 무관하게 유지된다

## 실행

```bash
./gradlew test   # 전송 규약·파트너 계약·플로우·확장 데모 47개 테스트
```

핵심 테스트: [`NewPartnerExtensionTest`](src/test/kotlin/karrot/partnerpos/NewPartnerExtensionTest.kt)
— 테스트 파일에만 존재하는 가상의 제4 파트너가 **메인 코드 수정 0줄**로 전체 플로우
(매장 활성화 → 주문 등록 → 재고 오버레이)에 참여하는 것을 증명한다.
