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
├── domain/     도메인별(order·menu·store·partner)로 나누고, 각 도메인 안을 model / application으로 구분
├── client/     외부 연동 구현 — transport(전송 규약) · direct(직연동) · legacy(레거시 포트)
├── infra/      domain의 포트에 꽂히는 어댑터 (인메모리 리포지토리·스텁)
└── config/     환경 데이터·시크릿 바인딩(yml)과 클라이언트 빈 구성
```

**패키지 규칙**
- `domain`은 비즈니스 규칙과 포트를 가진다 — model은 도메인 모델·계약, application은 유스케이스·조립·분기
- `client`는 외부 시스템과의 통신 구현이다
- `infra`는 기술 구현이다 — domain은 인터페이스만 알고, 구현은 여기서 꽂힌다
- `config`는 환경마다 달라지는 값이다 — 코드가 아닌 유일한 것

## 실행

```bash
./gradlew test   # 전송 규약·파트너 계약·플로우·확장 데모 47개 테스트
```

핵심 테스트: [`NewPartnerExtensionTest`](src/test/kotlin/karrot/partnerpos/NewPartnerExtensionTest.kt)
— 테스트 파일에만 존재하는 가상의 제4 파트너가 **메인 코드 수정 0줄**로 전체 플로우
(매장 활성화 → 주문 등록 → 재고 오버레이)에 참여하는 것을 증명한다.
