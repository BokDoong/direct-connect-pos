# 확장 가이드 — 무엇이 바뀌면, 어디를 추가하는가

> 이 구조에서 요구사항 유형별로 **무엇을 추가하고 무엇을 고치지 않는지**의 실무 가이드.
> 후반부에 사용한 패턴과, 재설계로 무엇이 수월해졌는지를 정리한다.

---

## 1. 확장 시나리오별 가이드

### A. 새 직연동 파트너 추가

**추가하는 것: 구현 클래스 1파일 + yml 1블록 + partners row(정체성) 1건. 고치는 것: 없음.**

```kotlin
// 1) client/direct/ShakeShackPartner.kt — 이 파일이 파트너의 전체 명세가 된다
@Component
class ShakeShackPartner(
    private val transport: PosApiTransport,
    props: DirectPosProperties,
) : DirectPosPartner, StockQueryable {              // ← 지원하는 capability만 선언

    override val key = PartnerKey("SHAKE_SHACK")
    override val policy = PartnerPolicy(unacceptedAutoCancel = 600.seconds)  // 계약값
    private val endpoint = props.endpointOf(key)

    override fun registerOrder(order: PosOrder) {
        transport.post(endpoint, REGISTER_ORDER_PATH, CommonOrderPayload.from(order))
    }
    // cancelOrder, fetchStocks …
}
```

```yaml
# 2) application.yml — 환경 데이터만
partner-pos:
  endpoints:
    SHAKE_SHACK:
      base-url: https://pos.shake-shack.example.com
      auth-key: ${SHAKE_SHACK_AUTH_KEY}
```

```sql
-- 3) partners row — 정체성만 (docs/06 §1). 누락하면 기동 대사가 배포 시점에 잡는다
INSERT INTO partners (name, partner_key, api_key, whitelist_ips) VALUES ('쉐이크쉑', 'SHAKE_SHACK', ...);
```

컴포넌트 스캔 → `List<DirectPosPartner>` 주입 → registry 자동 등록. 앱 계층 서비스,
기존 파트너, 계약 인터페이스는 커밋에 등장하지 않는다 — `PartnerKey`가 value class(열린 집합)라
공유 enum 수정도 없다.
증명: [`NewPartnerExtensionTest`](../src/test/kotlin/karrot/partnerpos/NewPartnerExtensionTest.kt) —
테스트 소스에만 존재하는 파트너가 메인 코드 수정 0줄로 기동 대사·StoreFinder 조립·전체 플로우에 참여한다.

### B. 새 API(capability) 추가 — 예: 매장 코드 검증 (실제 논의됐던 요구)

**추가하는 것: 인터페이스 1개 + 지원 파트너 구현 + 호출자 1곳. 고치는 것: 없음 (스키마 마이그레이션 0).**

```kotlin
// 1) domain/partner/model/ — 중립 계약 선언. "무엇이 변하는 축인지"를 이름으로 고정한다
interface StoreCodeVerifiable {
    fun verifyStoreCode(storeCode: StoreCode): StoreCodeVerification
}

// 2) 지원 파트너에만 구현 추가 (예: CjPartner에 `, StoreCodeVerifiable` + 메서드)
// 3) 호출자 — StoreFinder가 조립한 Store에서 꺼내고, 미지원은 타입 검사로 자연스럽게 건너뛴다
val partner = store.directPos?.partner
if (partner is StoreCodeVerifiable) partner.verifyStoreCode(store.directPos.partnerStoreCode)
```

AS-IS였다면: `supports_store_code_verification` 컬럼 추가 → 마이그레이션 → 전 row 백필 →
클라이언트 메서드 추가 → 호출 지점 게이트. **5단계 수술이 코드 추가만으로 줄어든 것이 이 재설계의 핵심 효용**이다.

### C. 파트너별 페이로드 확장 — 버거킹 패턴 재사용

파트너가 공통 규격에 없는 필드를 요구하면, **그 파트너 파일 안에서** 전용 페이로드를 조립한다:

```kotlin
// 파트너 파일 안 — 공통 규격을 부품으로 포함하고 필드를 더한다
data class ShakeShackOrderPayload(
    @field:JsonUnwrapped val common: CommonOrderPayload,
    val membershipCode: String,                    // 파트너 고유 요구
)

override fun registerOrder(order: PosOrder) {
    transport.post(endpoint, REGISTER_ORDER_PATH,
        ShakeShackOrderPayload(CommonOrderPayload.from(order), order.resolveMembership()))
}
```

규칙: **파트너 용어는 파트너 파일 밖으로 새지 않는다.** 공통 DTO(`CommonOrderPayload`)와
도메인 모델(`PosOrder`)에 특정 파트너 필드를 추가하고 싶어지면 그 순간 경계가 새는 것이다.

### D. 정책값 변경 — 예: 계약 갱신으로 자동취소 시간이 바뀔 때

해당 파트너 구현체의 `PartnerPolicy` 한 줄 수정 → PR 리뷰 → 배포.
AS-IS의 DB UPDATE(리뷰·이력 없음)와 달리 **계약 변경이 코드 리뷰와 git 이력을 통과**한다.

### E. 병렬 규칙이 필요해지면 — List 주입 패턴

지금 구조의 컬렉션 주입은 전부 "1개 선택(Map dispatch)"이다. 만약 "N개 모두 실행"이 필요한
축(예: 주문 등록 전 검증 규칙 — 매장코드·메뉴코드·한도 검증이 계속 늘어나는 경우)이 생기면
Validator 패턴으로 확장한다:

```kotlin
fun interface OrderRegistrationValidator {
    fun validate(order: PosOrder)          // 위반 시 도메인 예외
}

@Service
class OrderPlacementService(
    private val posOrderWriter: PosOrderWriter,
    private val posOrderSynchronizer: PosOrderSynchronizer,
    private val validators: List<OrderRegistrationValidator>,   // ← 규칙 추가 = 빈 추가
) {
    fun place(store: Store, order: PosOrder) {
        validators.forEach { it.validate(order) }               // 서비스는 한 줄도 안 바뀐다
        // …
    }
}
```

기준: **골라 쓰면 Map, 모두 실행하면 List.** 파트너 선택은 전자, 검증 규칙은 후자.

## 2. 사용한 패턴과 채택 이유

| 패턴 | 적용 위치 | 왜 이것인가 |
|---|---|---|
| **전략(Strategy) + Map dispatch** | `DirectPosPartner` ← `DirectPosPartnerRegistry` | 파트너 선택은 닫힌 1:1 매핑 — 런타임 dispatch를 `when` 분기 대신 Map 색인으로. 기동 시 중복 키 fail-fast |
| **capability 인터페이스 분리 (ISP)** | `StockQueryable`, `StoreRegistrable` | `supports_*` boolean 컬럼의 타입화 — 지원 여부와 구현이 한 파일에서 움직이고, 게이트 누락이 컴파일 에러가 됨 |
| **상속 대신 컴포지션** | `PosApiTransport`·`CommonOrderPayload`를 파트너 구현체에 주입 | 변하는 행동 축이 "페이로드 조립" 하나로 격리됨 → abstract base 상속의 대가(부모 변경 전파, 단일 상속 슬롯 소진, 테스트 시 골격 구동)를 지불할 이유가 없음 |
| **Super type token + reified** | `PosApiTransport.postForBody` | 제네릭 소거 하에서 `List<T>` 역직렬화 타입을 안전하게 전달. Kotlin reified 오버로드로 호출부에서 토큰 은닉 |
| **값 객체 (value class)** | `PartnerKey`, `OrderCode`, `StoreCode`, `MenuCode` | 문자열 뒤섞임 방지 + `PartnerKey`는 열린 집합(enum이면 파트너 추가마다 공유 파일 수정 → OCP 위배) |
| **보상 트랜잭션** | `OrderPlacementService` | 등록 성공 후 저장 실패 → 즉시 취소 전파(유령주문 방지). 전파 실패는 원인 예외를 가리지 않음 |
| **정책의 코드화** | `PartnerPolicy` | 계약값을 리뷰·이력 가능한 자산으로. 환경 데이터(base_url·auth_key)만 yml/시크릿에 |
| **호출자 소유의 실패 정책** | `PurchaseStage.failureMode` | soft/hard는 파트너가 아니라 구매 단계의 속성 — 파트너 계층에 넣지 않는 절제가 곧 추상화의 정확도 |

## 3. 무엇이 수월해졌나 — 참고 글의 검증 기준으로

참고: [면접에서 "확장성"을 말하는 순간 면접관은 이것부터 확인한다](https://medium.com/greglee-lab/77383ee3fb25).
글이 제시한 검증 지점 4개에 이 구조를 대면:

### ① "변하는 축을 식별했는가"

- **AS-IS**: 변하는 축을 "파트너별 설정값"으로만 봤다. 값으로 표현되는 동안(롯데)은 유효했지만,
  행동이 변하는 순간(버거킹 페이로드) 표현 수단이 없었다.
- **TO-BE**: 축을 4개로 분해했다 — 파트너 선택(Map) / capability(인터페이스 구현) /
  페이로드(파트너 소유 조립) / 정책값(코드 VO). 그리고 **변하지 않는 것**(전송 규약)과
  **파트너와 무관한 것**(soft/hard)을 추상화에서 제외했다.

### ② "서비스를 고치지 않고 요건을 추가할 수 있는가" (OCP)

| 변경 | AS-IS | TO-BE |
|---|---|---|
| 새 파트너 | row INSERT (무배포) — 단, 페이로드가 같을 때만 | 구현 1파일 + yml + partners row(정체성) — 페이로드가 달라도 (배포 동반 수용) |
| 새 API/capability | 컬럼 추가 + 마이그레이션 + 백필 + 클라이언트 메서드 + 게이트 산개 | 인터페이스 1개 + 지원 파트너 구현 + 호출자 1곳 |
| 페이로드 확장 | **불가능** (임계점) | 파트너 파일 안의 전용 DTO 조립 |
| 검증 규칙 추가 (향후) | 서비스 if 누적 | `List<Validator>` 빈 추가, 서비스 무수정 |

### ③ "상속과 컴포지션의 대가를 계산했는가"

초기 설계는 공통 골격을 abstract base class(템플릿 메서드)로 두는 안이었다.
글의 기준 — *"변하는 훅이 하나로 격리됐다면 상속이 아니라 인터페이스로 뽑아라"* — 을 적용해보니
파트너별로 변하는 행동은 페이로드 조립 하나였고, 나머지 차이는 값(policy·path)과 타입(capability)이었다.
그래서 골격 상속을 버리고 공통 부품(transport·조립기) 주입으로 전환했다. 결과:
롯데 구현체는 부모 골격 없이도 12줄이고, 부품은 파트너와 독립적으로 테스트된다
([`PosApiTransportTest`](../src/test/kotlin/karrot/partnerpos/client/transport/PosApiTransportTest.kt)).

### ④ "계약에 구현체 용어가 새지 않는가"

- 계약 계층의 이름: `registerOrder`, `fetchStocks`, `PartnerPolicy`, `orderCode` — 어느 파트너의
  스펙 용어도 아니다. 글의 `googleSub`/`kakaoId` 안티패턴에 해당하는 것(예: 계약에 `paymentMethodForBurgerKing`
  같은 필드)이 없다.
- 버거킹의 결제수단 코드 매핑(`toBurgerKingCode`)은 `BurgerKingPartner.kt` 안의 private 함수다.
  **파트너 용어의 생존 범위 = 파트너 파일 하나** — 이 경계가 다음 파트너를 받아들일 여지다.

### 그리고 테스트 — 글에는 없지만 체감이 가장 큰 것

AS-IS에서 파트너별 동작은 DB 상태의 함수라 fixture 없이는 테스트할 수 없었다.
TO-BE에서 파트너는 생성자 주입되는 평범한 객체다:

- 파트너 **계약** 테스트: mock HTTP 서버로 "이 파트너가 실제로 만드는 요청"을 검증
- **플로우** 테스트: fake 파트너를 주입해 보상·soft/hard·활성화 분기를 DB 없이 검증
- **확장** 테스트: 존재하지 않는 파트너를 테스트 안에서 만들어 구조의 약속 자체를 검증

## 4. 남긴 것 (의도된 스코프 경계)

- 인바운드 콜백: 당근 규격 단일 스펙이라 파트너별 분기 자체가 없음 — 재설계 대상 아님 (01 §4)
- 결제 보상·SQS 예약·재고 비동기 반영: 위치만 의사코드로 표시 (04 §9)
- FOODTECH/HAPPYORDER 유형: 규격 주도권이 반대(파트너 규격에 당근이 맞춤)라 별도 축 — 직연동 구조에 집중
