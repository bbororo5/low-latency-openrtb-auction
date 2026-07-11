# Architecture-Significant Requirements

상태: Workshop Draft 0.2
현재 검토 범위: `2. Selection Method`

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

1. Purpose and Scope — reviewed
2. Selection Method ← current
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
- `OOS-007`의 외부 partner onboarding과 public-internet security 운영; `A-003`의 trusted billable-event source 가정이 무너지면 Requirements로 되돌아간다.

### 1.5 Output Boundary

최종 ASR은 최소한 다음을 가진다.

- business rationale와 authoritative requirement source
- source, stimulus, environment, artifact, response, response measure
- 해당하는 경우 절대 완화하지 않는 safety invariant
- 해당하는 경우 안전성을 지키기 위해 허용할 수 있는 degradation
- 구현 이전에 분석하거나 구현 이후 실험할 verification method

한 ASR과 하나의 Architecture Driver 또는 ADR은 1:1 관계가 아니다. Driver는 여러 ASR이 만드는 우선순위와 충돌을 종합하고, ADR은 그 압력에서 발생한 구체적 decision question을 다룬다.

### 1.6 Section Acceptance Gate

이 section은 다음에 합의할 때 완료된다.

- ASR의 정의와 이 문서의 역할이 명확하다.
- Requirements와 ASR의 경계가 명확하다.
- 현재 구현과 이전 측정이 normative input이 아니다.
- solution, Driver, ADR, architecture description을 미리 선택하지 않는다.
- 후속 section에서 사용할 최종 ASR의 최소 구성요소가 정의되어 있다.

## 2. Selection Method

이 방법은 [SEI QAW](https://www.sei.cmu.edu/library/quality-attribute-workshops-qaws-third-edition/)의 prioritized/refined quality scenario와 [ATAM](https://www.sei.cmu.edu/library/architecture-tradeoff-analysis-method-collection/)의 scenario, risk, trade-off 구분을 개인 프로젝트에 맞게 경량화한다. 정식 stakeholder workshop과 architecture evaluation을 수행한다고 주장하지 않는다.

### 2.1 Analysis Unit

ASR은 원본 요구사항의 한 문장과 1:1로 선정하지 않는다. 하나의 ASR은 여러 goal, functional requirement, quality requirement, constraint, workload profile이 함께 만드는 품질 시나리오일 수 있다. 반대로 하나의 requirement가 서로 다른 자극과 측정법을 가진 여러 ASR로 분리될 수 있다.

선별 단위는 requirement ID가 아니라 `business consequence + quality scenario + architectural consequence`다.

### 2.2 Candidate Derivation

후보는 다음 순서로 만든다.

1. product goal이 실패하는 구체적 상황을 찾는다.
2. 그 상황을 만드는 load, fault, concurrency, timing, data, change stimulus를 찾는다.
3. 단일 requirement가 아니라 서로 충돌하는 requirement 조합을 찾는다.
4. 시스템의 구조를 바꾸지 않고는 달성하기 어려운지 반례를 든다.
5. 품질 속성 목록은 누락 검사에만 사용하고, 목록에 있다는 이유로 후보를 만들지 않는다.

합의 전 후보는 `CAND-{CONCERN}-{NN}`로 표시한다. `CONCERN`은 `TIME`, `MONEY`, `FAILURE`, `LOAD`, `DATA`, `CHANGE`, `OPERATE` 중 주된 관점을 나타내며 품질 속성이나 설계 결론을 의미하지 않는다.

유사 업계 사례와 외부 수치는 비교와 반례를 얻는 calibration input이다. 해당 사례의 수치를 이 프로젝트의 requirement로 채택하려면 별도의 business assumption과 검증 환경이 필요하다.

### 2.3 Scenario Refinement

각 후보는 다음 여섯 요소로 정제한다.

| Element | Question |
|---|---|
| Source | 누가 또는 무엇이 stimulus를 발생시키는가? |
| Stimulus | 시스템에 정확히 무슨 일이 발생하는가? |
| Environment | 정상, peak, overload, 장애, 복구, 배포 중 언제인가? |
| Artifact | 어떤 service, state, boundary, operation이 영향을 받는가? |
| Response | 시스템은 관찰 가능한 어떤 의미 있는 행동을 해야 하는가? |
| Response Measure | 어떤 수치나 불변식으로 성공과 실패를 판별하는가? |

시나리오에는 추가로 business rationale, authoritative source, primary quality attribute를 붙인다. safety invariant와 allowed degradation은 정합성·안전성·가용성처럼 구분이 필요한 후보에만 붙인다.

### 2.4 Data and Distribution Lens

모든 후보에 다음 질문을 적용한다. 답이 없다고 특정 database나 분산 기술을 고르지 않는다. 먼저 요구 의미가 부족한지 확인한다.

- 상태의 authoritative owner와 source of truth는 누구인가?
- 상태는 언제 생성되고, 외부 약속이 되며, 확정·해제·만료되는가?
- 어느 acknowledgment 이후에 장애가 발생해도 기억해야 하는가?
- concurrent, duplicate, reordered, conflicting operation의 도메인 의미는 무엇인가?
- stale, partial, unavailable state를 어디까지 허용하는가?
- 인스턴스와 AZ 장애 후 보존할 상태와 버려도 되는 work는 무엇인가?
- cardinality, access pattern, skew, hot key가 응답과 coordination에 어떤 부하를 만드는가?

### 2.5 Significance Filter

후보는 다음 필수 조건을 모두 만족해야 ASR 선별 대상이 된다.

1. business goal, requirement, constraint, workload profile에 추적된다.
2. 구체적 stimulus, environment, response, response measure가 있다.
3. 요구를 완화하거나 제거했을 때 system 분해, topology, data, coordination, boundary, verification 중 하나 이상이 의미 있게 달라진다.
4. 특정 솔루션을 요구하지 않고 외부에서 관찰할 보장을 설명한다.
5. 구현이나 실험으로 pass/fail을 판별할 수 있다.

다음은 architectural significance를 더 강하게 만드는 징후이지만 필수 조건은 아니다.

- 여러 subsystem과 운영 경계를 횡단한다.
- 나중에 뒤집기 비싸거나 migration risk가 크다.
- 현실적인 접근법이 둘 이상이며 품질 속성 간 trade-off가 있다.
- 현재 지식만으로 feasibility를 확신하기 어렵다.

이 필터는 ASR 자격을 판별한다. 자격을 통과한 후보 간 우선순위는 section 3의 rubric으로 따로 판단한다.

### 2.6 Split and Merge Rules

다음 경우에는 후보를 분리한다.

- stimulus 또는 environment가 다르다.
- 시스템 response와 response measure가 독립적이다.
- safety invariant와 SLO처럼 완화 가능성이 다르다.
- 동일한 장애가 가용성과 상태 보존에 서로 다른 반응을 요구한다.

다음 조건을 모두 만족할 때만 후보를 합친다.

- 같은 business consequence를 다룬다.
- stimulus, environment, response, measure가 의미상 같다.
- 합쳐도 설계 압력과 검증 방식이 흐려지지 않는다.

하나의 business scenario가 SSP, Our DSP, state boundary를 횡단한다는 이유로 component별 ASR로 쪼개지 않는다.

### 2.7 Candidate Disposition

검토된 후보는 다음 중 하나로 처리한다.

| Disposition | Meaning |
|---|---|
| Promote | significance filter를 통과했으며 priority 합의 후 공식 ASR로 등록할 수 있다. |
| Return to Requirements | product semantics, trust boundary, failure allowance, measurement condition이 부족하다. |
| Exclude as Non-ASR | 유효한 requirement이지만 architecture를 의미 있게 바꾸지 않는다. |
| Reframe as Decision Question | solution이나 선택지를 요구사항으로 잘못 표현했다. Driver 후의 입력으로만 보류한다. |
| Record as Risk | requirement는 명확하지만 feasibility나 비용이 불확실하다. Driver에서 검증 필요성을 다룬다. |

제외된 모든 후보를 문서에 남기지는 않는다. 후속 단계에서 재등장할 가능성이 큰 경계 사례만 section 8에 제외 이유와 함께 남긴다.

### 2.8 Promotion Gate

후보는 다음을 모두 충족한 후에 `ASR-NNN`을 부여받는다.

- authoritative input으로 추적된다.
- 미해결 product semantics가 없다.
- 여섯 가지 scenario element가 완성되어 있다.
- 완화 또는 제거할 때 달라지는 architectural consequence를 설명할 수 있다.
- solution-neutral하며 pass/fail을 관찰할 수 있다.
- section 3의 우선순위 평가를 거쳐 사용자가 승인했다.

### 2.9 Solo-Project Roles

별도의 stakeholder workshop을 열 수 없으므로 다음 역할을 의도적으로 분리한다.

| Role | Responsibility |
|---|---|
| User | product owner와 architect로서 business meaning, loss tolerance, priority, final acceptance를 결정한다. |
| Assistant | facilitator, senior peer, adversarial reviewer, researcher, scribe로서 문의 순서와 반례를 제시하고 합의된 내용만 기록한다. |
| Tests and evidence | 사람의 주장을 대체하지 않으며 feasibility, risk, implementation conformance를 반복 검증한다. |

분석할 때는 advertiser, SSP operator, Our DSP operator, external integrator, developer, interviewer 관점의 반례를 각각 검토하되, 존재하지 않는 stakeholder의 요구를 새로 발명하지 않는다.

### 2.10 Section Acceptance Gate

이 section은 다음에 합의할 때 완료된다.

- requirement ID 복사가 아닌 scenario 단위로 후보를 만든다.
- 자격 판정과 우선순위 평가를 구분한다.
- 데이터와 분산 시스템 질문을 특정 솔루션 선택 전에 적용한다.
- split, merge, exclusion, return, promotion 기준이 명확하다.
- 외부 사례와 기존 구현이 requirement를 자동으로 결정하지 않는다.
- 최종 판단과 기록의 책임이 분리되어 있다.
