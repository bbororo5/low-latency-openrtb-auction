# Architecture-Significant Requirements

상태: Workshop Draft 0.1
현재 검토 범위: `1. Purpose and Scope`

입력 문서:

- [Product and Domain Requirements](../requirements/product-and-domain-requirements.md)
- [Workload, Data and Verification Profile](../requirements/workload-data-and-verification-profile.md)

이 문서는 확정된 ASR baseline이 아니다. 각 section을 하나씩 검토하고, 합의된 내용만 다음 검토 단계의 입력으로 사용한다.

## Review Protocol

1. 한 번에 하나의 section만 본문으로 작성하고 검토한다.
2. 합의 전 시나리오는 `CAND-*` working label로 구분하며 문서에 공식 ASR로 등록하지 않는다.
3. `ASR-NNN`은 품질 속성 시나리오, 중요도, architecture impact, 추적성이 확인된 후에만 부여한다.
4. 요구사항의 의미가 불분명하면 architecture 해결책으로 메우지 않고 Requirements로 되돌아간다.
5. product, topology, protocol, data store, consistency mechanism은 ADR 이전에 선택하지 않는다.
6. Architecture Drivers, ADR, architecture description은 각각의 생성 조건이 충족되기 전에 파일을 만들지 않는다.

## Planned Sections

1. Purpose and Scope ← current
2. Selection Method
3. Priority Rubric
4. Prioritized ASR Summary
5. Detailed Quality Attribute Scenarios
6. ASR Interactions
7. Requirement and Verification Traceability
8. Non-ASR Boundaries
9. Ready-for-Drivers Criteria

## 1. Purpose and Scope

### 1.1 Purpose

이 문서는 전체 Requirements를 다시 요약하지 않는다. 시스템의 구조와 비싼 설계 결정에 measurable effect를 가지는 요구만 선별하여 다음 단계의 입력으로 제공한다.

이 프로젝트에서 ASR은 해당 요구의 변경이 다음 중 하나 이상을 의미 있게 바꾸는 요구사항이다.

- system 분해 및 deployment/failure topology
- data ownership, lifecycle, consistency, durability
- concurrency, ordering, deadline, resource coordination
- system boundary를 넘는 contract와 failure semantics
- 핵심 품질을 입증하기 위한 verification structure

이 문서는 각 ASR을 솔루션이 아닌 검증 가능한 품질 속성 시나리오로 표현한다. 합의된 ASR은 Architecture Drivers에서 우선순위와 충돌 관계를 종합할 근거가 된다.

### 1.2 Authoritative Inputs

| Input | Role in ASR analysis |
|---|---|
| Product and Domain Requirements | product goal, actor, domain semantics, functional/quality requirement, constraint, assumption, out-of-scope의 권위 있는 출처 |
| Workload, Data and Verification Profile | traffic, dataset, skew, event/fault mix, latency/recovery target, verification environment의 권위 있는 출처 |

현재 구현과 과거 performance evidence는 ASR의 성공 기준을 낮추는 근거가 아니다. 다만 후속 설계에서 불확실성과 위험을 찾는 실험 입력으로는 사용할 수 있다.

### 1.3 Included Concerns

- SSP와 Our DSP의 핵심 runtime path 및 외부 actor와의 boundary
- auction deadline, tail latency, logical DSP fan-out, partial failure isolation
- campaign candidate lookup, dataset cardinality, skew, hot budget contention
- budget exposure, committed spend, idempotency, ordering, durability, reconciliation
- overload, bounded resource, recovery, instance/AZ failure, rolling deployment
- 품질 신호의 observability와 재현 가능한 verification
- 구조나 경계를 바꾸는 핵심 기능 요구사항과 제약

### 1.4 Excluded Content

- 일반 기능 요구사항의 재기술
- database, cache, broker, framework, cloud product 선택
- replica 수, load balancer, gateway, partition, leader/quorum 구조
- token, quota, escrow, locking, batch accounting 같은 money 구현 전략
- 아직 대안 비교가 없는 ADR 예상 목록
- 구현 작업 계획, 코드 구조, 현재 성능 결과
- Requirements에서 명시적으로 제외한 제품 범위

### 1.5 Output Boundary

최종 ASR은 최소한 다음을 가진다.

- business rationale와 authoritative requirement source
- source, stimulus, environment, artifact, response, response measure
- 절대 완화하지 않는 safety invariant
- 안전성을 지키기 위해 허용할 수 있는 degradation
- 구현 이전에 분석하거나 구현 이후 실험할 verification method

한 ASR과 하나의 Architecture Driver 또는 ADR은 1:1 관계가 아니다. Driver는 여러 ASR이 만드는 우선순위와 충돌을 종합하고, ADR은 그 압력에서 발생한 구체적 decision question을 다룬다.

### 1.6 Section Acceptance Gate

이 section은 다음에 합의할 때 완료된다.

- ASR의 정의와 이 문서의 역할이 명확하다.
- Requirements와 ASR의 경계가 명확하다.
- 현재 구현과 이전 측정이 normative input이 아니다.
- solution, Driver, ADR, architecture description을 미리 선택하지 않는다.
- 후속 section에서 사용할 최종 ASR의 최소 구성요소가 정의되어 있다.
