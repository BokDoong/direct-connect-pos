# 카프카 0-to-100 시리즈

아파치 카프카를 "왜 만들어졌는가"부터 "직접 만들어보기"까지 10편으로 완주하는 학습 시리즈다. 기준 버전은 **Apache Kafka 4.x — KRaft 전용(ZooKeeper 제거 완료)** 이며, 각 문서의 섹션에는 난이도(🟢 기초 / 🟡 실무 / 🔴 심화)가 표기되어 있다. 모든 편의 끝에는 면접 포인트(좋은 답변·나쁜 답변)가 붙어 있고, 07·08편은 이 리포지토리의 직연동 POS 주문 도메인을 예시로 사용한다.

---

## 문서 색인

| 파일 | 제목 | 난이도 | 한 줄 요약 |
|---|---|---|---|
| [00-why-kafka.md](00-why-kafka.md) | 왜 카프카인가 | 🟢 | N×M 통합 문제와 전통 메시지 큐의 한계에서 출발해 "분산·복제되는 append-only 커밋 로그"라는 핵심 아이디어를 세운다 |
| [01-core-concepts.md](01-core-concepts.md) | 핵심 개념 | 🟢 | 레코드·토픽·파티션·오프셋·브로커·컨슈머 그룹 — 시리즈 전체가 딛고 설 어휘를 관계 중심으로 완성한다 |
| [02-storage-internals.md](02-storage-internals.md) | 저장 내부 구조 | 🟡 | 세그먼트·희소 인덱스·페이지 캐시·zero-copy로 "디스크를 쓰는데 왜 빠른가"를 해부하고 retention·컴팩션·tiered storage까지 다룬다 |
| [03-replication-and-controller.md](03-replication-and-controller.md) | 복제와 컨트롤러 | 🟡🔴 | ISR·LEO·HW·acks·min.insync.replicas로 "유실 없는 카프카의 조건"을 정의하고 KRaft 컨트롤러와 리더 선출을 파고든다 — **시리즈의 기준 문서** |
| [04-producer-deep-dive.md](04-producer-deep-dive.md) | 프로듀서 심화 | 🟡🔴 | 배칭 파이프라인, 재시도와 순서 역전, 멱등 프로듀서(PID·시퀀스), 트랜잭션과 좀비 펜싱, exactly-once의 정확한 범위 |
| [05-consumer-deep-dive.md](05-consumer-deep-dive.md) | 컨슈머 심화 | 🟡🔴 | poll 루프, 두 개의 죽음 감지 축, 오프셋 커밋 시맨틱스, 리밸런싱(eager→cooperative→KIP-848), 랙·seek, 셰어 그룹(KIP-932) |
| [06-operations-and-tuning.md](06-operations-and-tuning.md) | 운영과 튜닝 | 🔴 | 파티션 수 산정, 핵심 모니터링 지표, 브로커 사이징, 장애 시나리오별 대응(디스크 풀·리밸런싱 폭풍·핫 파티션·poison pill), 보안·쿼터·압축 |
| [07-ecosystem.md](07-ecosystem.md) | 카프카 생태계 | 🟡 | Kafka Connect, CDC/Debezium, Schema Registry, Kafka Streams, ksqlDB, MirrorMaker 2 — 코어 위에 얹히는 도구와 선택 기준 |
| [08-architecture-patterns.md](08-architecture-patterns.md) | 아키텍처 패턴 | 🔴 | 이벤트 3분류, 아웃박스, 컨슈머 멱등, 파티션 키 설계, DLQ·재시도 토픽, CQRS·이벤트 소싱, 사가, 멀티 리전 DR |
| [09-build-your-own-kafka.md](09-build-your-own-kafka.md) | 카프카를 직접 만들어보기 | 🟡🔴 | 로그 파일부터 리더 선출까지 8개 마일스톤으로 미니 카프카를 구현하며 전 시리즈를 손으로 검증하는 로드맵 |

---

## 추천 읽기 경로

### 경로 1 — 주니어: 개념부터 차근차근

> **00 → 01 → 04 → 05 → 02 → 03**, 이후 여유가 될 때 06 → 07 → 08 → 09

- 00·01로 "카프카가 무엇이고 어휘가 무엇인지"를 먼저 굳힌다.
- 그다음 실제로 코드를 만지게 되는 클라이언트 양쪽(04 프로듀서, 05 컨슈머)을 읽는다. 🔴 섹션(트랜잭션, KIP-848 상세)은 처음엔 건너뛰어도 된다.
- 내부 원리인 02(저장)·03(복제)은 클라이언트 감각이 생긴 뒤에 읽어야 "왜 이런 설정이 있는가"가 손에 잡힌다.
- 06~08은 운영·설계 책임이 생길 때, 09는 전체를 복습하고 싶을 때.

### 경로 2 — 시니어 속성: 보장과 설계 판단만 빠르게

> **03 → 04(§4~6) → 05(§2~5, §8) → 06 → 08**, 필요할 때 02·07을 참조, 09는 선택 실습

- 이미 메시징 시스템 경험이 있다면 카프카의 차별점은 결국 **보장(유실·중복·순서)의 정확한 경계**다. 그 경계를 정의하는 03(ISR·acks·KRaft)부터 시작한다.
- 04의 멱등성·트랜잭션·EOS 범위, 05의 커밋 시맨틱스·리밸런싱 프로토콜 진화가 그 위에 쌓인다.
- 06으로 운영 의사결정(파티션 수·지표·장애 대응)을, 08로 아키텍처 패턴(아웃박스·멱등·DLQ·사가)을 채우면 설계 리뷰와 면접 대응이 완성된다.

---

## 핵심 용어집

| 개념 | 한 줄 정의 | 상세 문서 |
|---|---|---|
| 커밋 로그 (commit log) | 끝에만 덧붙이고 수정하지 않는, 순서가 곧 시간인 append-only 자료구조 — 카프카의 핵심 아이디어 | [00](00-why-kafka.md) |
| 레코드 (record) | key·value·timestamp·headers로 구성된 카프카 데이터 한 건 | [01](01-core-concepts.md) |
| 토픽 (topic) | 레코드의 논리적 분류 단위, 이름 붙은 통로 | [01](01-core-concepts.md) |
| 파티션 (partition) | 토픽을 쪼갠 독립 append-only 로그 — 병렬성의 단위이자 순서의 단위 | [01](01-core-concepts.md) |
| 오프셋 (offset) | 파티션 내 레코드의 순번이자 컨슈머 그룹의 "책갈피" | [01](01-core-concepts.md) |
| 브로커 (broker) | 카프카 서버 프로세스 — 여럿이 모여 클러스터를 이룬다 | [01](01-core-concepts.md) |
| 컨슈머 그룹 (consumer group) | 파티션을 나눠 맡는 컨슈머 집합 — 그룹 안은 큐, 그룹 사이는 pub-sub | [01](01-core-concepts.md) |
| 스티키 파티셔닝 (sticky partitioning) | key 없는 레코드를 배치 단위로 한 파티션에 몰아 담아 배치 효율을 높이는 기본 파티셔너 동작 | [01](01-core-concepts.md), [04](04-producer-deep-dive.md) |
| 세그먼트 (segment) | 파티션 로그를 쪼갠 파일 단위 — 파일명이 베이스 오프셋, retention 삭제의 단위 | [02](02-storage-internals.md) |
| 희소 인덱스 (sparse index) | 4KiB마다 한 엔트리만 두는 오프셋→파일위치 인덱스 — 작아서 페이지 캐시에 상주 | [02](02-storage-internals.md) |
| 페이지 캐시 (page cache) | 카프카가 자체 캐시 대신 그대로 활용하는 OS 커널 캐시 — fsync 대신 복제로 내구성을 확보 | [02](02-storage-internals.md) |
| zero-copy (sendfile) | 페이지 캐시의 데이터를 유저 공간을 거치지 않고 소켓으로 직송하는 전송 경로 — TLS를 쓰면 무효화 | [02](02-storage-internals.md) |
| 보존 정책 (retention) | 소비 여부와 무관하게 시간·크기 기준으로 데이터 수명을 정하는 정책 (`retention.ms`/`retention.bytes`) | [02](02-storage-internals.md) |
| 로그 컴팩션 (log compaction) | 키별 최신 value만 남기는 세그먼트 재작성 정책 (`cleanup.policy=compact`) | [02](02-storage-internals.md) |
| 툼스톤 (tombstone) | value=null 레코드 — 컴팩션 토픽에서 해당 키의 삭제를 표현 | [02](02-storage-internals.md) |
| tiered storage (KIP-405) | 닫힌 세그먼트를 원격 저장소(S3 등)로 내려 보존 기간과 로컬 디스크를 분리하는 기능 | [02](02-storage-internals.md) |
| 복제 팩터 (replication factor) | 파티션 사본의 개수 — 실무 표준은 3 | [03](03-replication-and-controller.md) |
| 리더/팔로워 (leader/follower) | 파티션 단위의 단일 쓰기 지점과, 리더를 Fetch로 당겨 복제하는 사본들 | [03](03-replication-and-controller.md) |
| ISR (In-Sync Replicas) | 리더 + 리더의 로그 끝을 시간 기준(`replica.lag.time.max.ms`)으로 따라잡고 있는 팔로워들의 동적 명단 | [03](03-replication-and-controller.md) |
| LEO (Log End Offset) | 레플리카 로그의 끝 — 다음에 기록될 메시지가 받을 오프셋 | [03](03-replication-and-controller.md) |
| HW (High Watermark) | ISR 전원이 보유한 지점(ISR LEO의 최솟값) — 컨슈머는 이 앞까지만 읽는다 | [03](03-replication-and-controller.md) |
| acks / min.insync.replicas | 쓰기 성공 판정 시점과 그것이 유효하기 위한 ISR 최소 크기 — RF=3 + acks=all + min.isr=2가 실무 표준 | [03](03-replication-and-controller.md) |
| unclean 리더 선출 | ISR 전멸 시 뒤처진 레플리카를 리더로 승격할지의 선택 — 가용성 vs 정합성 | [03](03-replication-and-controller.md) |
| 리더 에포크 (leader epoch) | 파티션의 몇 번째 리더 체제인지 나타내는 단조 증가 카운터 — 좀비 차단과 로그 truncation의 기준 | [03](03-replication-and-controller.md) |
| KRaft | 메타데이터를 카프카 자신의 로그(`__cluster_metadata`)와 Raft 합의로 관리하는 컨트롤러 구조 — 4.0부터 유일한 방식 | [03](03-replication-and-controller.md) |
| 멱등 프로듀서 (idempotent producer) | PID + 파티션별 시퀀스 넘버로 재시도 중복과 순서 역전을 브로커가 걸러내는 기능 — 3.0부터 기본 | [04](04-producer-deep-dive.md) |
| 트랜잭션 / 좀비 펜싱 | `transactional.id`로 세션을 넘는 정체성을 만들고 epoch로 옛 인스턴스를 차단하며, 다중 파티션 쓰기를 원자화 | [04](04-producer-deep-dive.md) |
| exactly-once (EOS) | 카프카 토픽→토픽 파이프라인 안에서만 성립하는 처리 시맨틱스 — 외부 시스템이 끼면 멱등 설계의 몫 | [04](04-producer-deep-dive.md), [08](08-architecture-patterns.md) |
| 오프셋 커밋 (offset commit) | "다음에 읽을 오프셋"을 `__consumer_offsets`에 저장하는 행위 — 커밋 시점이 유실/중복을 가른다 | [05](05-consumer-deep-dive.md) |
| 그룹 코디네이터 (group coordinator) | 그룹의 멤버십·하트비트·커밋을 책임지는 브로커 — `__consumer_offsets` 파티션 리더가 겸직 | [05](05-consumer-deep-dive.md) |
| 리밸런싱 (rebalancing) | 그룹 멤버십 변화 시의 파티션 재할당 — eager→cooperative→KIP-848(브로커 주도 증분)로 진화 | [05](05-consumer-deep-dive.md) |
| 컨슈머 랙 (consumer lag) | LEO − 커밋 오프셋, "밀린 메시지 수" — 컨슈머 시스템의 제1 건강 지표 | [05](05-consumer-deep-dive.md), [06](06-operations-and-tuning.md) |
| auto.offset.reset | 유효한 커밋 오프셋이 없을 때만 발동하는 시작 위치 규칙(earliest/latest/none) | [05](05-consumer-deep-dive.md) |
| 셰어 그룹 (share group, KIP-932) | 파티션 소유권 없이 레코드 단위 ack로 소비하는 큐 시맨틱스 — 파티션 수 병렬성 상한을 넘는다 | [05](05-consumer-deep-dive.md) |
| 핫 파티션 (hot partition) | 키 쏠림으로 특정 파티션만 뜨거워지는 현상 — 키 설계·솔팅으로 대응 | [06](06-operations-and-tuning.md), [08](08-architecture-patterns.md) |
| poison pill | 처리가 불가능해 파티션 소비를 막는 메시지 — 재시도 토픽·DLQ로 격리 | [06](06-operations-and-tuning.md), [08](08-architecture-patterns.md) |
| Kafka Connect | 외부 시스템↔카프카 데이터 이동을 커넥터 플러그인과 설정으로 표준화한 프레임워크 | [07](07-ecosystem.md) |
| CDC / Debezium | DB의 변경 로그(binlog/WAL)를 구독해 카프카로 흘리는 변경 데이터 캡처와 그 사실상 표준 구현 | [07](07-ecosystem.md) |
| Schema Registry | 스키마를 중앙 등록하고 ID만 메시지에 실으며, 호환성 검사로 프로듀서-컨슈머 계약을 강제하는 장치 | [07](07-ecosystem.md) |
| Kafka Streams | 별도 클러스터 없이 라이브러리로 동작하는 스트림 처리 — KStream/KTable, 상태 저장소, EOS | [07](07-ecosystem.md) |
| MirrorMaker 2 | Connect 기반의 클러스터 간 비동기 복제 — 오프셋 번역, 접두사 기반 루프 방지 | [07](07-ecosystem.md) |
| 아웃박스 패턴 (outbox) | 이벤트를 같은 DB 트랜잭션으로 아웃박스 테이블에 저장하고 CDC로 릴레이해 이중 쓰기 문제를 푸는 패턴 | [08](08-architecture-patterns.md) |
| DLQ (Dead Letter Queue) | 재시도 계층을 소진한 실패 메시지를 격리하는 토픽 — 원본 메타데이터 보존과 재주입 절차가 세트 | [08](08-architecture-patterns.md) |
| 사가 (saga) | 분산 트랜잭션을 로컬 트랜잭션 연쇄 + 보상 트랜잭션으로 대체하는 패턴 — 코레오그래피 vs 오케스트레이션 | [08](08-architecture-patterns.md) |

---

*첫 편부터 시작하기: [00-why-kafka.md](00-why-kafka.md)*
