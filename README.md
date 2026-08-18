# 주문 시스템 (order-system)

포장주문(픽업) **주문 시스템**입니다. 첫 번째 도메인으로 **직연동 POS 연동(CJ푸드빌·롯데GRS·버거킹)** 아웃바운드 계층을 담고 있으며, "파트너별 차이를 DB row로 파라미터화"하던 기존 구조를 **전략 + capability 인터페이스 + 컴포지션** 구조로 재설계했습니다. 영속 계층은 H2 + JPA로 구성되어 있습니다.

> **한 줄 요약**: 기존 설계(row INSERT 확장)는 롯데 온보딩을 코드 변경 0으로 끝낼 만큼 유효했지만,
> 버거킹의 "주문 등록 페이로드에 결제수단 추가" 요구처럼 **파트너별 행동 차이**가 등장하는 순간
> 표현 수단이 없다. 이 프로젝트는 그 임계점에서 변하는 축을 다시 식별해 경계를 옮긴 기록이다.

## 문서 읽는 순서

| # | 문서 | 내용 |
|---|---|---|
| 1 | [docs/cores/01-as-is-integrated-partner.md](docs/cores/01-as-is-integrated-partner.md) | AS-IS 구조 — enum+DB row 분기, 스키마, API, "DB에 있던 설정" 인벤토리 |
| 2 | [docs/cores/02-as-is-outbound-flows.md](docs/cores/02-as-is-outbound-flows.md) | AS-IS 아웃바운드 4종의 비즈니스 플로우와 실패 모드 — 추상화 경계(seam)의 발견 |
| 3 | [docs/cores/03-to-be-design-discussion.md](docs/cores/03-to-be-design-discussion.md) | 문제 정의(P1~P7), 설계 결정(D1~D5)과 근거, 결정 로그 |
| 4 | [docs/cores/04-to-be-architecture.md](docs/cores/04-to-be-architecture.md) | TO-BE 아키텍처 — 변하는 축 분석, 컴포넌트 구조도, 상호작용, 확장 시나리오 검증 |
| 5 | [docs/cores/05-extension-guide.md](docs/cores/05-extension-guide.md) | 확장 가이드 — 시나리오별 추가 지점, 사용한 패턴, 무엇이 수월해졌는가 |
| 6 | [docs/cores/06-db-integration.md](docs/cores/06-db-integration.md) | DB 연동 — 테이블별 운명, 코드↔DB 기동 대사, 트랜잭션 경계, 마이그레이션 경로 |
| 7 | [docs/cores/07-core-structure.md](docs/cores/07-core-structure.md) | Core 구조 스냅샷 — 계층·영속 컨벤션·조립 경로·컴포넌트 맵·테스트 맵 |

**학습 시리즈**: [docs/kafka/](docs/kafka/README.md) — 카프카 0-to-100 이론 시리즈 10편 (기초 → 내부 원리 → 운영·설계 → 직접 구현 로드맵, 난이도 태그·면접 포인트 포함)

## 패키지 구조

```
src/main/kotlin/karrot/partnerpos/
├── domain/     도메인별(order·menu·store·pos)로 나누고, 각 도메인 안을 model / application으로 구분
├── client/     외부 연동 구현 — transport(전송 규약) · direct(직연동) · legacy(레거시 포트)
├── infra/      영속 계층 — JPA 엔티티(*Entity) + Spring Data 리포지토리 (H2) + 레거시 클라이언트 스텁
└── config/     환경 데이터·시크릿 바인딩(yml)과 클라이언트 빈 구성
```

**패키지 규칙**
- `domain`은 비즈니스 규칙을 가진다 — model은 도메인 모델·계약, application은 유스케이스·조립·분기
- 영속 컨벤션: 테이블은 `*s`, 엔티티는 `*Entity`(infra), **Finder가 리포지토리를 주입받아 entity를 도메인 모델로 변환**해 돌려준다
- `client`는 외부 시스템과의 통신 구현이다
- `config`는 환경마다 달라지는 값이다 — 코드가 아닌 유일한 것

## 실행

```bash
./gradlew test      # 전송 규약·파트너 계약·타입 분기·영속(H2)·확장 데모 50개 테스트
./gradlew bootRun   # H2 인메모리로 기동 — 콘솔: http://localhost:8080/h2-console
```

핵심 테스트: [`NewPartnerExtensionTest`](src/test/kotlin/karrot/partnerpos/NewPartnerExtensionTest.kt)
— 테스트 파일에만 존재하는 가상의 제4 파트너가 **메인 코드 수정 0줄**로 전체 플로우
(매장 활성화 → 주문 등록 → 재고 오버레이)에 참여하는 것을 증명한다.
