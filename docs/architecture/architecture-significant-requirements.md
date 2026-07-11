# Architecture-Significant Requirements

상태: Workshop Draft 0.2
현재 검토 범위: `2. Selection Method`

입력:

- [Product and Domain Requirements](../requirements/product-and-domain-requirements.md)
- [Workload, Data and Verification Profile](../requirements/workload-data-and-verification-profile.md)

이 문서는 확정된 baseline이 아니다. 목차를 하나씩 검토하고 합의된 내용만 유지한다.

## Working Rules

- 합의 전 후보는 `CAND-*`, 승인된 후보만 `ASR-NNN`으로 표시한다.
- 요구사항이 불분명하면 설계로 메우지 않고 Requirements로 되돌아간다.
- product, topology, protocol, data store, consistency mechanism은 ADR 이전에 선택하지 않는다.
- 설명과 학습 내용은 대화에서 다루고, 문서에는 후속 판단에 필요한 규칙과 결과만 남긴다.

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

ASR은 요구의 변경이 다음 중 하나 이상을 의미 있게 바꾸는 요구사항이다.

- system 분해와 deployment/failure topology
- data ownership, lifecycle, consistency, durability
- concurrency, ordering, deadline, resource coordination
- system boundary의 contract와 failure semantics
- 핵심 품질을 입증하는 verification structure

권위 있는 출처는 상위 Requirements 두 문서다. 현재 구현과 과거 측정은 성공 기준을 낮추는 근거가 아니며, 후속 위험과 feasibility를 검증하는 입력으로만 사용한다.

이 문서는 deadline/fan-out, campaign·money data, overload, instance/AZ failure, recovery, observability, 변경성 중 architecture에 실제 영향을 주는 관심사를 다룬다.

다음은 다루지 않는다.

- 일반 기능 요구사항의 재기술
- product·topology·data store·coordination mechanism 선택
- ADR 예상 목록, 구현 계획, 현재 성능 결과
- Requirements의 out-of-scope; `A-003`의 trusted billable-event source 가정이 무너지면 security 설계로 메우지 않고 Requirements로 되돌아간다.

최종 ASR은 business rationale, requirement source, 6요소 품질 시나리오, verification method를 갖는다. safety invariant와 allowed degradation은 필요한 경우에만 추가한다.

## 2. Selection Method

이 방법은 [SEI QAW](https://www.sei.cmu.edu/library/quality-attribute-workshops-qaws-third-edition/)의 quality scenario refinement와 [ATAM](https://www.sei.cmu.edu/library/architecture-tradeoff-analysis-method-collection/)의 scenario·risk·trade-off 구분을 개인 프로젝트에 맞게 경량화한다.

### 2.1 Flow

1. product goal이 실패하는 구체적 상황을 찾는다.
2. load, fault, concurrency, timing, data, change stimulus로 `CAND-{CONCERN}-{NN}`을 만든다.
3. 후보를 6요소 품질 시나리오로 정제한다.
4. data/distribution 질문과 significance filter를 적용한다.
5. section 3에서 우선순위를 합의한 후보만 `ASR-NNN`으로 승격한다.

선별 단위는 requirement 한 줄이 아니라 `business consequence + quality scenario + architectural consequence`다. 업계 사례와 외부 수치는 calibration input이며, 별도의 사업 가정 없이 이 프로젝트의 requirement가 되지 않는다.

### 2.2 Quality Scenario

| Element | Question |
|---|---|
| Source | 누가 또는 무엇이 사건을 발생시키는가? |
| Stimulus | 정확히 무슨 일이 발생하는가? |
| Environment | 정상, peak, overload, 장애, 배포 중 언제인가? |
| Artifact | 어떤 service, state, boundary가 영향받는가? |
| Response | 시스템은 어떤 관찰 가능한 행동을 해야 하는가? |
| Response Measure | 어떤 수치나 불변식으로 성공을 판별하는가? |

### 2.3 Data and Distribution Lens

- state owner와 source of truth는 누구인가?
- 상태는 언제 외부 약속이 되고 확정·해제·만료되는가?
- 어느 acknowledgment 이후에 장애가 나도 보존해야 하는가?
- concurrent, duplicate, reordered, conflicting operation의 도메인 의미는 무엇인가?
- stale/partial state, skew, hot key, failure가 response에 어떤 압력을 만드는가?

### 2.4 Eligibility and Disposition

후보는 다음을 모두 만족해야 ASR 자격을 갖는다.

- authoritative requirement에 추적된다.
- stimulus, environment, response, response measure가 구체적이다.
- 요구를 완화하면 topology, data, coordination, boundary, verification 중 하나 이상이 달라진다.
- solution-neutral하며 구현이나 실험으로 pass/fail을 판별할 수 있다.

stimulus, environment, response measure, 완화 가능성이 다르면 후보를 분리한다. 같은 business consequence와 scenario를 가지고 합쳐도 설계 압력이 흐려지지 않을 때만 합친다.

| Disposition | Meaning |
|---|---|
| Promote | 자격과 우선순위를 통과한다. |
| Return to Requirements | product semantics나 측정 조건이 부족하다. |
| Exclude as Non-ASR | 유효한 requirement이지만 architecture impact가 작다. |
| Reframe as Decision Question | solution이나 선택지를 requirement로 잘못 표현했다. |

feasibility, cost, evidence gap은 disposition과 독립적인 risk로 표시한다. 제외된 후보 중 후속 단계에서 재등장할 가능성이 큰 경계 사례만 section 8에 남긴다.
