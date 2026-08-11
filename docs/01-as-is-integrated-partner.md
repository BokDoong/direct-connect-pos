# AS-IS: 직연동(INTEGRATED_PARTNER) 기존 구조

> 픽업(포장주문) 서비스의 외부 POS 연동 4유형(KARROT / FOODTECH / HAPPYORDER / INTEGRATED_PARTNER) 중
> **직연동(INTEGRATED_PARTNER)** 의 기존 설계를 정리한 문서.
> 근거: 운영 런북(노션) + 개발 당사자 기억. `❓` 표시는 런북에 없어 당사자 확인이 필요한 항목.

---

## 1. 직연동이란

- 중간 플랫폼(푸드테크·해피오더) 없이 **프랜차이즈 본사 시스템과 당근이 직접 연결**하는 방식.
- 다른 연동과 결정적 차이: **규격 주도권이 당근에 있다.** 당근이 만든 공통 규격서에 파트너(CJ올리브네트웍스, 롯데이노베이트)가 맞춰 구현했다.
- 파트너 현황:

| 파트너 | 브랜드 | 오픈 | 특징 |
|---|---|---|---|
| CJ푸드빌 (CJ올리브네트웍스 구현) | 뚜레쥬르 | 2026-03-25 | 재고 실시간 조회 지원, 매장 등록/해지 API 지원 |
| 롯데GRS (롯데이노베이트 구현) | 롯데리아·엔제리너스·크리스피크림 | 2026-04-28 | 재고 미지원(품절 신호만), 매장 등록 API 없음(수기 협의), 미수락 자동취소 5분, 롯데리아는 주문 자동수락(POS측 동작 — 당근 시스템과 무관) |
| 버거킹 (차기 과제, 미오픈) | 버거킹 | — | **주문 등록 페이로드에 결제수단 필드 추가 요구**(당근페이 결제수단을 문자열 매핑해 전달) — 파트너별 페이로드 확장의 최초 사례, row INSERT로 흡수 불가. capability는 재고 조회 ✗ / 매장 등록 ✓ (CJ·롯데와 또 다른 제3의 조합) |

## 2. 분기 축 — enum 하나 + DB row

- `stores.partner_type` enum 4값이 시스템 전체 분기의 시작점. **CJ·롯데는 별도 enum이 아니라 둘 다 `INTEGRATED_PARTNER`.**
- 개별 파트너의 차이는 **`partners` 테이블 row 값**으로 표현 → **새 직연동 파트너 추가 = enum 추가가 아니라 row INSERT** (무배포 확장이 설계 목표).
- 파트너 공통 인터페이스(전략 패턴)는 없었고, HTTP Client 3종(`FoodTechClient`/`HappyOrderClient`/`PartnerClient`)만 인터페이스. 도메인 레벨 분기는 `when(partnerType)`이 각 서비스에 분산.

## 3. DB 스키마

### 3-1. `partners` — 파트너 마스터 (CJ/롯데 차이의 전부)

| 컬럼 | 역할 | 성격 |
|---|---|---|
| `name` (ux) | URL path의 `{partnerType}` 값과 case-insensitive 문자열 비교로 검증 (`@ValidatePartnerType` AOP) | 식별 |
| `api_key` (ux, char36) | 토큰 발급용 키 | 인증(시크릿) |
| `whitelist_ips` (json) | 토큰 발급 시 IP 검증 | 인증 |
| `base_url` | 아웃바운드 호출 대상 서버 | 라우팅(환경) |
| `auth_key` | 아웃바운드 Bearer 값 (⚠️ 평문 저장 TODO 상태였음) | 인증(시크릿) |
| `supports_stock` | true=재고 Pull 호출(CJ), false=skip(롯데). **재고 조회 6개 호출 지점 전부의 게이트** | capability 플래그 |
| `supports_store_registration` | 매장 활성화 시 registerStore 호출 여부 게이트 | capability 플래그 |
| `order_delayed_accept_seconds` (기본 600) | 미수락 자동취소 대기초 — CJ 600 / 롯데 300. SQS 지연 메시지 delay 값으로 사용 | 정책값 |
| `register_order_api_path` / `cancel_order_api_path` | 주문 등록/취소 경로 override (기본 `/api/v1/karrot-pickup/orders/register`, `…/orders/{orderId}/cancel` — cancel은 orderId placeholder 필수) | 라우팅 |

✅ 당사자 확인: 위 컬럼이 전부 (추가 컬럼 없음).

### 3-2. `partner_stores` — 매장 side-table

| 컬럼 | 역할 |
|---|---|
| `partner_id` + `partner_store_code` (복합 uk) | 파트너 구분 + 파트너측 매장 식별. 대량 입점 시 코드 점유 충돌 차단의 DB 근거 |
| `is_pos_open` (+ 갱신시각 컬럼) | POS 개/폐점 신호의 마지막 상태 — 매장 오픈 3조건 중 하나 |
| `deleted_at` | **soft-delete = 연동 해지.** 행을 남겨 재입점 시 이력 추적 |

### 3-3. `partner_orders` — 주문 매핑

| 컬럼 | 역할 |
|---|---|
| `order_id` (uk) | **중복 등록 멱등성을 DB 유니크 제약이 최종 방어.** 위반 시 코드가 AlreadyRegistered(409)로 해석 |
| `partner_id`, `registered_at` | 어느 파트너로 나갔는지 / 등록 시각 |

- 주문번호는 **당근이 채번**한 `orders.order_code`(varchar(16) uk, YYMMDD+Base36)를 파트너에 전달. 파트너 콜백도 이 코드로 들어옴 → 역추적 키.
- 16자인 이유: 롯데 시스템 주문번호 칸이 varchar(20) 제한.

### 3-4. `partner_tokens` — 인바운드 인증 토큰

- `access_token` / `refresh_token` char(36) 각 ux. UUID opaque, access 1일 / refresh 30일.
- 발급 시 해당 파트너의 기존 토큰 전체 삭제(rotate) — 파트너당 단일 토큰셋.

## 4. 인바운드 API (파트너 → 당근)

인증: `PartnerAuthInterceptor`가 `/partner/**` 전체에 `X-PARTNER-TOKEN` 검증. 예외는 토큰 발급 2개 경로뿐.

> **인바운드는 파트너별 분기가 없다.** 규격 주도권이 당근에 있어 인바운드 요구사항은 파트너가 당근 단일
> 스펙에 맞춰 구현해줬다 (CJ·롯데·버거킹 공통). 파트너별 "다름"은 아웃바운드에만 존재하며,
> 따라서 TO-BE 재설계 스코프도 아웃바운드로 한정한다.

| Method | 경로 | 용도 |
|---|---|---|
| POST | `/partner/token` · `/partner/token/refresh` | 토큰 발급/갱신 (api_key + IP whitelist 검사) |
| POST | `/partner/{partnerType}/api/v1/stores/{storeCode}/status` | 매장 개/폐점 |
| POST | `…/stores/bulk-sync-status` | 개/폐점 일괄 동기화 (30분 주기, 비동기 즉시 200 → 결과 슬랙 통지) |
| POST | `…/stores/{storeCode}/menus/{menuCode}/status` | 메뉴 상태 (OPEN / SOLD_OUT / CLOSE) |
| POST | `…/orders/{orderId}/accept` · `/preparation-complete` · `/pickup-completed` · `/cancel` | 주문 상태 변경 콜백. 조리완료 재호출 시 픽업콜 재전송(최대 3회) |

- 모든 인바운드 요청은 `partner_api_request_logs`에 요청/응답 본문까지 감사 저장.
- **멱등 계약**(PR #2781, 2026-06 전 연동사 표준화): 요청 상태 == 현재 상태면 200 no-op, 상태 충돌이면 400 + body에 `currentOrderStatus` 병기.

## 5. 아웃바운드 API (당근 → 파트너)

클라이언트: `PartnerApiClient` — RestClient connect 3s / read 10s, **`@Retryable` 4회 지수백오프 (5xx·타임아웃만, 4xx 제외)**, `auth_key`를 Bearer로 전송. 요청 로그는 Loki.

- **성공/실패 판정은 HTTP status만 사용** (body resultCode 없음). 전 파트너 공통.
- **요청/응답 페이로드 스키마는 CJ·롯데 100% 동일** (당근 규격서 기준). 파트너별로 달랐던 것은 base_url·path·auth_key(→ DB 저장 이유)와 capability(롯데는 매장 등록/재고 API 미제공)뿐.

| Method | 경로 | 용도 | 게이트 |
|---|---|---|---|
| POST | `{base_url}{register_order_api_path}` | 주문 등록 | — |
| POST | `{base_url}{cancel_order_api_path}` | 주문 취소 (당근발 취소만 전파) | — |
| POST | `{base_url}/api/v1/karrot-pickup/stocks` | 재고 조회 | `supports_stock=true` |
| POST | `{base_url}/api/v1/karrot-pickup/stores/registration` | 매장 등록/해지 | `supports_store_registration=true` |

## 6. 주문 라이프사이클

```
결제(당근페이 gRPC)
 → 주문 등록 (PartnerOrderRegistrar → PartnerApiClient) + partner_orders 매핑 저장
    └ 등록 성공 후 저장 실패 시: 즉시 취소 전파 (유령주문 방지 보상 트랜잭션)
 → SQS 지연메시지 예약 (delay = order_delayed_accept_seconds)
    └ 미수락이면 자동취소 + POS 접속 원장(15분 배치) 대조해 "POS 꺼짐" 케이스 슬랙 알림
 → 수락 콜백 → EventBridge +24h 예약 ({orderId}.auto-pickup-complete)
 → 조리완료 콜백 → 고객 픽업콜 (최대 3회)
 → 픽업완료 콜백 (누락 시 24h 후 자동완료)
```

- **취소 방향 규칙**: 누가 취소하든(고객/매장/어드민/자동) 당근이 취소+환불을 먼저 확정하고, **당근발 취소만** POS로 HTTP 전파. 전파 실패해도 당근 취소는 유지 (best-effort, Sentry 로깅만 — `PartnerPosCanceler`).
- 이미 취소된 주문 재취소 전파는 200 멱등 처리 (2026-05 롯데 재취소 404 장애의 산물).

## 7. 재고 연동 (Pull — supports_stock=true만)

**규격**: 요청 = 메뉴코드 리스트, 응답 = (메뉴코드 + 수량) DTO 리스트.

호출 지점 6곳과 실패 정책 — 화면 조회는 soft, 구매 경로는 hard:

| 시점 | 컴포넌트 | 실패 시 |
|---|---|---|
| 카테고리/메뉴판 조회 | `DisplayCategoryService` | DB 값 fallback (soft) |
| 메뉴 상세 | `CustomerMenuService` | DB 값 fallback (soft) |
| 장바구니 담기 | `CartStockValidator` | **담기 차단 (hard)** |
| 장바구니 조회 | `CartMenuStockFinder` | 해당 메뉴 품절 처리 (soft) |
| 주문 생성 | `CreateOrderService` | **주문 차단 (hard)** |
| 결제 승인 직전 | `PartnerOrderStockChecker` → `PartnerStockFinder` | **주문 차단 (hard)** |

- 롯데는 재고 미지원 → 인바운드 품절 신호(`SOLD_OUT`)만 반영, 품절은 자동 해제 안 됨(판매재개 신호 필요).

## 8. 매장 라이프사이클

```
어드민 매장 생성 (AdminPartnerStoreLinker → partner_stores side-table 생성)
 → 활성화 시 PartnerStoreActivator가 /stores/registration 호출 (supports_store_registration 게이트, CJ만)
 → 파트너 POS가 개점 신호 → is_pos_open=true → 노출 후보
 → 운영상태 판정 (조회 시마다 실시간): 영업시간 내 && 수동폐점 아님 && is_pos_open
 → 연동 해지 = partner_stores.deleted_at (soft-delete). 롯데는 등록/해지 API가 없어 수기 협의
```

- 롯데는 대신 "매장 등록 여부 조회 API"를 제공 → 양측 매장 목록 대사(검수)용.

## 9. "DB에 있던 설정" 인벤토리 — TO-BE 설계의 원료

기존 구조에서 파트너별 차이를 담던 곳을 성격별로 재분류하면:

| 성격 | 항목 | AS-IS 위치 | 비고 |
|---|---|---|---|
| 라우팅(환경) | base_url, register/cancel path | partners row | 환경마다 다른 **데이터** |
| 인증(시크릿) | api_key, auth_key, whitelist_ips | partners row (auth_key 평문) | 시크릿 스토어 대상 |
| capability | supports_stock, supports_store_registration | partners row (boolean) | **행동 분기** — 코드 6곳+의 게이트로 산개 |
| 정책값 | order_delayed_accept_seconds | partners row (int) | SQS delay로 주입 |
| 정책(공통 하드코딩) | 픽업콜 재전송 최대 3회, 취소 사유 "가게 사정" 고정, 주문코드 16자 당근 채번(롯데 varchar(20) 제한 대응, 전 파트너 공통) | 코드 하드코딩 ✅확인 | 직연동 내 공통이라 문제되지 않았음 |
| **페이로드 확장 요구** | 버거킹: 주문 등록 페이로드에 **결제수단** 필드 추가 요구 | **표현 수단 없음** | row INSERT로 흡수 불가 — 최초의 "코드 변경이 필요한 파트너" |

참고 — 파트너별 차이처럼 보이지만 당근 시스템과 무관한 것들 (분기 대상 아님):
- 롯데리아 자동수락: POS측 동작. 당근은 주문 등록 API를 쏘고, 수락 콜백이 사장님 손이냐 파트너 시스템이냐는 파트너 소관.
- CJ 새벽 3시 전 매장 일괄 마감: 파트너 서버측 동작 (당근은 폐점 신호를 받을 뿐).
- 컵보증금(+300원): 파트너 정책이 아니라 메뉴 옵션 설정(DB)의 문제.
- 할인 표기 방식(CJ 2종 분리 / 롯데 대표 1건): 페이로드는 동일, 파트너측 화면 표기 차이.

> **TO-BE 재설계의 핵심 동기**: 파트너의 "다름"이 boolean/int/varchar 컬럼으로 표현되는 동안만
> "row INSERT 확장"이 성립했다. 실제로 롯데 온보딩은 코드 변경 0으로 끝났다 — 이 설계는 제 역할을 다했다.
> 임계점은 버거킹이 넘었다: **페이로드 스키마가 파트너마다 달라지는 순간**, 차이는 더 이상 데이터가 아니라
> 행동이고, 이를 담을 자리가 기존 구조에는 없다.

## 10. 컴포넌트 맵 (직연동 관련)

| 컴포넌트 | 앱 | 역할 |
|---|---|---|
| `PartnerStoreController` / `PartnerOrderController` | pickup-merchant | 인바운드 `/partner/{partnerType}/api/v1/*` |
| `PartnerOrderService` + Acceptor/Completer/Canceler | pickup-merchant | 콜백 → 도메인 상태 갱신 |
| `PartnerAuthInterceptor` | pickup-merchant | 토큰 인증 |
| `PartnerOrderRegistrar` | pickup-core | 주문 등록 라우터 (partnerType 분기) |
| `PartnerOrderSynchronizer` | pickup-core | 직연동 주문 동기화 |
| `PartnerPosCanceler` | pickup-core | 취소 전파 (best-effort) |
| `PartnerOrderStockChecker` → `PartnerStockFinder` | pickup-core | 재고 Pull |
| `StoreOperationFinder` | pickup-core | 운영상태 판정 |
| `PartnerApiClient` | infra/clients | 아웃바운드 HTTP (3s/10s, Retryable 4회) |
| `AdminPartnerStoreLinker` / `PartnerStoreActivator` | pickup-admin | 온보딩 (side-table 생성 / 활성화 시 등록 호출) |
| SQS → `DelayedOrderAcceptHandler` | pickup-worker | 미수락 자동취소 |
| EventBridge → `InternalEventBridgeHandler` | pickup-admin | 24h 자동 픽업완료 |

## 11. 직연동 관련 장애·의사결정 이력

| 사건 | 결과 |
|---|---|
| CJ KYC 미인증 30일 일괄 해지 사고 (2026-04-24, 85~164개 매장) | 정책 변경: "연동은 유지, 정산만 보류". KYC를 활성화 게이트에서 제외 |
| 롯데 재취소 404 (2026-05-06) | 재취소 로직 수정 + 이미 취소된 주문 재취소는 200 멱등 |
| 롯데 컵보증금 옵션 코드 오타 (`DPST`↔`DSPT`) 전 주문 오과금 | 옵션 코드 정확성이 과금에 직결됨을 확인 — 코드 검수 필요성 |
| 해피오더발 정합성 계약 (2025-11-25 장애) | 직연동 규격서에 표준으로 반영: 상태 응답에 currentOrderStatus 병기, 멱등 계약 |
| CJ 이른 아침 재고 부정확 (06시 일괄 반영 구조) | 구조적 한계로 수용 (파트너측 문제) |
| 배민 행사 트래픽 → CJ 서버 잠식 → 당근 재고조회 타임아웃 (2026-07) | 프로모션 일정 상호 공유 필요 (운영 대응) |

---

## 12. ✅ 당사자 확인으로 확정된 사실 (2026-08-10)

런북에 없던 내용 중 개발 당사자 확인으로 채워진 것들. 본문에 모두 반영 완료.

1. **페이로드**: CJ·롯데 요청/응답 스키마 100% 동일. 파트너별 차이는 base_url·path·auth_key(그래서 DB에 저장)와 capability(롯데 매장등록·재고 API 미제공)가 전부.
2. **partners 컬럼**: 문서 기재 컬럼이 전부 (추가 없음).
3. **재고 조회 규격**: 요청 = 메뉴코드 리스트 / 응답 = (메뉴코드+수량) DTO 리스트.
4. **성공/실패 판정**: HTTP status만 사용.
5. **하드코딩 정책**: 픽업콜 최대 3회, 취소 사유 "가게 사정" 고정 — 코드단 하드코딩 (직연동 공통이라 문제 없었음). 자동수락은 POS측 동작으로 당근 시스템과 무관.
6. **롯데 온보딩**: 실제로 row INSERT만으로 완료, 코드 변경 0. 주문번호는 전 파트너(CJ·롯데·버거킹) 당근 채번으로 통일. 컵보증금은 메뉴 옵션 설정의 영역.
7. **차기 파트너 버거킹**: 주문 등록 페이로드에 **결제수단** 필드 추가 요구(당근페이 결제수단의 문자열 매핑) — 기존 구조의 임계점이자 이번 재설계(TO-BE)의 핵심 확장 시나리오. capability는 재고 ✗ / 매장 등록 ✓.
8. **인바운드는 파트너별 차이 없음**: 당근이 규격 주도권을 가져 파트너들이 당근 단일 스펙에 맞춰 구현. 재설계 스코프는 아웃바운드로 한정.
