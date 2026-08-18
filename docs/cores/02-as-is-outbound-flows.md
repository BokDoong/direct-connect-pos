# AS-IS: 직연동 아웃바운드 — 비즈니스 플로우 상세

> 리팩토링 전 단계로, 아웃바운드 호출 4종이 **어떤 비즈니스 플로우 안에서, 언제, 왜 불리고,
> 실패하면 무슨 일이 생기는지**를 100% 파악하기 위한 문서.
> 추상화의 경계(seam)는 이 플로우에서 발견되어야 한다.
> 미확인 항목 14건은 2026-08-10 당사자 확인으로 전부 해소됨 (§6).

---

## 0. 아웃바운드 호출 지도

| # | API | 트리거 (비즈니스 이벤트) | 호출 컴포넌트 | 게이트 |
|---|---|---|---|---|
| 1 | 주문 등록 `POST {base_url}{register_path}` | 고객 결제 완료 | `PartnerOrderRegistrar` → `PartnerApiClient` | — |
| 2 | 주문 취소 `POST {base_url}{cancel_path}` | 당근발 취소 확정 (고객/운영자/미수락 자동취소/유령주문 보상) | `PartnerPosCanceler` → `PartnerApiClient` | 당근발 취소만 |
| 3 | 재고 조회 `POST {base_url}/api/v1/karrot-pickup/stocks` | 고객 구매 여정 6개 지점 | `PartnerStockFinder` ← 각 지점 서비스 | `supports_stock` |
| 4 | 매장 등록/해지 `POST {base_url}/api/v1/karrot-pickup/stores/registration` | 어드민 매장 활성화 / 연동 해지 | `PartnerStoreActivator` | `supports_store_registration` |

공통 전송 계층: `PartnerApiClient` — RestClient connect 3s / read 10s, Bearer(`auth_key`),
`@Retryable` 4회 지수백오프(5xx·타임아웃만, 4xx 제외), 성공/실패 판정은 HTTP status만, 요청 로그 → Loki.

---

## 1. 주문 등록 플로우 (결제 → 파트너 등록)

런북 기준으로 재구성한 시퀀스:

```
고객 결제 시도
 → (재고 파트너면) 결제 승인 직전 재고 최종 확인 (PartnerOrderStockChecker) — 실패 시 주문 차단 (hard)
 → 당근페이 결제 승인 (gRPC)
 → 파트너 주문 등록 (PartnerOrderRegistrar → HTTP) + partner_orders 매핑 저장
     ├ 등록 성공 + 저장 실패 → 즉시 취소 전파 (유령주문 방지)
     └ 등록 실패 → 역순 보상: 결제 취소, 재고·할인 원복
 → SQS 지연메시지 예약 (delay = order_delayed_accept_seconds) — 미수락 자동취소 타이머
```

**확인된 사실**
- 순서는 결제 승인이 먼저, 파트너 등록이 나중 (등록 실패 시 "역순으로 전부 취소").
- `partner_orders.order_id` 유니크 제약이 중복 등록의 최종 방어선 (위반 → AlreadyRegistered 409 해석).
- 주문번호는 당근 채번 `order_code` 16자를 전달. 응답 판정은 HTTP status만.

**✅ 확인된 동작 (당사자)**
- **동기 흐름**: 파트너 등록은 고객의 주문/결제 요청 응답을 잡고 있는 동기 호출. 재시도 4회 포함
  최악 수십 초를 고객이 대기하는 구조였다. (TO-BE 논의 후보 — §6 비고)
- **응답 body**: 200 + 공통 응답 형식이 왔지만 **활용하지 않았다** (판정은 HTTP status만).
  직연동은 파트너측 주문번호 회신 없이 당근 `order_code` 단일 키.
- **중복 등록 방지**: 타임아웃 후 재시도의 중복 위험은 **order_code 기준 멱등 처리를 당근 규격서로
  파트너에 요구**해서 해소 — 재시도 4회가 안전한 근거.
- **최종 실패 시**: 결제 자동 취소 (보상 트랜잭션).

## 2. 주문 취소 전파 플로우

```
취소 발생원: 고객 / 운영자(어드민) / 미수락 자동취소(SQS) / 유령주문 보상
 → 당근이 취소 + 환불 먼저 확정 (파트너 응답과 무관하게)
 → PartnerPosCanceler가 POS로 취소 전파 (best-effort)
     └ 실패해도 당근 취소 유지, Sentry 로깅만
```

**확인된 사실**
- 매장발 취소는 인바운드(POS→당근)로 들어오므로 전파 대상이 아님 — 전파는 당근발만.
- 이미 취소된 주문 재취소 전파는 파트너가 200 멱등 처리 (2026-05 롯데 404 장애 이후 규격화).
- 수락 후 취소 시 파트너에 전달되는 사유는 "가게 사정" 고정 (코드 하드코딩).

**✅ 확인된 동작 (당사자)**
- cancel path의 `{orderId}` = 당근 `order_code`. 취소 요청 body에도 `order_code` 포함.
  사유는 "가게 사정" 고정 (코드 하드코딩).
- **@Retryable 4회는 모든 아웃바운드 API 공통 적용** (취소 포함). 최종 실패 시 Sentry 로깅으로 종료
  (best-effort — 당근 취소는 이미 확정 상태).

## 3. 재고 조회 플로우 (Pull — supports_stock=true만)

규격: 요청 = 메뉴코드 리스트 / 응답 = (메뉴코드+수량) DTO 리스트.

| 지점 | 컴포넌트 | 실패 시 | 성격 |
|---|---|---|---|
| ① 카테고리/메뉴판 조회 | `DisplayCategoryService` | DB 값 fallback | soft |
| ② 메뉴 상세 | `CustomerMenuService` | DB 값 fallback | soft |
| ③ 장바구니 담기 | `CartStockValidator` | 담기 차단 | **hard** |
| ④ 장바구니 조회 | `CartMenuStockFinder` | 해당 메뉴 품절 처리 | soft |
| ⑤ 주문 생성 | `CreateOrderService` | 주문 차단 | **hard** |
| ⑥ 결제 승인 직전 | `PartnerOrderStockChecker` | 주문 차단 | **hard** |

**✅ 확인된 동작 (당사자)**
- **조회 범위**: 메뉴판/카테고리(①②)는 화면의 전체 메뉴코드, 장바구니~결제(③~⑥)는 담긴 메뉴만.
- **응답 수량은 DB에 저장하고 고객에게 노출**. 단, 저장은 **비동기** — 응답 경로는 조회 결과를 바로
  쓰고, DB 반영은 뒤에서 따라간다.
- **수량 검증까지 수행**: 품절(0) 판정뿐 아니라 "장바구니 수량 > 재고 수량" 차단 같은 수량 비교도 했다.
- **캐시 없음, 재시도 있음**: 메뉴 정보는 캐시(Redis)했지만 재고는 캐시 없이 매번 실시간 조회.
  재고 조회에도 @Retryable 4회 공통 적용 — 고객 요청 경로에서도 동일한 재시도 정책.

## 4. 매장 등록/해지 플로우

```
어드민 매장 활성화
 → AdminPartnerStoreLinker: partner_stores side-table 생성
 → PartnerStoreActivator: /stores/registration 호출 (supports_store_registration=true만, 즉 CJ)
 → 파트너가 매장 인지 → 이후 POS 개점 신호 시작

연동 해지
 → partner_stores.deleted_at (soft-delete)
 → 해지도 /stores/registration로 전파 (등록/해지 겸용 엔드포인트)
```

**✅ 확인된 동작 (당사자)**
- 요청 body에 `storeCode`를 담아 호출 (등록/해지 겸용 엔드포인트 — 구분값의 정확한 필드 형태는
  기억 상 미상, 구현 시 body 내 구분 필드로 가정).
- **매장 등록은 활성화 플로우에 포함된 hard 의존**: 등록 로직이 활성화 과정 안에 있어,
  등록이 실패하면 활성화 자체가 실패한다. (capability에 따라 활성화 플로우가 갈리는 지점 —
  롯데는 게이트 skip으로 바로 활성화)

## 5. 공통 전송 계층

**✅ 확인된 동작 (당사자)**
- `PartnerApiClient` 한 클래스에 4개 API(주문 등록/취소, 재고 조회, 매장 등록/해지) 메서드가 전부 있었다.
- `auth_key` Bearer는 **만료 없는 고정 값** (갱신 개념 없음 — 인바운드 토큰 rotate와 대비).

---

## 6. ✅ 확인 결과 요약 (2026-08-10 당사자 확인 — 전 항목 해소)

| # | 플로우 | 확인 결과 |
|---|---|---|
| 1 | 주문 등록 | **동기 흐름** — 고객이 파트너 등록 완료(최악 재시도 4회)까지 대기 |
| 2 | 주문 등록 | 200 + 공통 응답 body가 왔으나 **미활용** (status만 판정). 파트너 주문번호 회신 없음 |
| 3 | 주문 등록 | **order_code 멱등을 규격서로 파트너에 요구** — 재시도 안전성의 근거 |
| 4 | 주문 등록 | 최종 실패 시 결제 자동 취소 |
| 5 | 취소 전파 | path·body 모두 order_code 사용. 사유 "가게 사정" 하드코딩 |
| 6 | 취소 전파 | @Retryable 4회는 **전 API 공통**. 실패 시 Sentry 종료 (수동 재전파 없음) |
| 7 | 재고 조회 | 메뉴판 = 전체 메뉴 / 장바구니~결제 = 담긴 메뉴만 |
| 8 | 재고 조회 | 응답 수량을 **DB에 비동기 저장** + 고객 노출 |
| 9 | 재고 조회 | 품절 판정 + **수량 비교 검증**(장바구니 수량 > 재고 차단)까지 수행 |
| 10 | 재고 조회 | 재고는 캐시 없음(메뉴만 캐시), 재시도 4회 동일 적용 |
| 11 | 매장 등록 | body에 storeCode. 등록/해지 겸용 (구분 필드 형태는 미상 — 구현 시 가정) |
| 12 | 매장 등록 | 등록이 활성화 플로우에 포함된 hard 의존 — 등록 실패 = 활성화 실패 |
| 13 | 공통 | PartnerApiClient 한 클래스에 4 API 메서드 전부 |
| 14 | 공통 | auth_key는 만료 없는 고정 Bearer |

**TO-BE 논의로 넘길 관찰** (확장성과 별개로 플로우에서 드러난 특성):
- 재시도의 정확한 위치 (당사자 정정): HTTP/API 레이어 재시도는 없었고, **파트너를 호출하는 클라이언트
  컴포넌트(`PartnerApiClient`)에 `@Retryable` 4회**가 붙어 있었다. TO-BE도 같은 위치(공통 전송 부품)에
  동일하게 둔다 — order_code 멱등 계약이 재시도 안전성을 보장하므로.
- 재고 응답의 비동기 DB 반영 — 조회(동기)와 반영(비동기)이 분리된 구조. 재설계 시 StockFinder의
  반환값과 저장 이벤트를 분리해야 함.
