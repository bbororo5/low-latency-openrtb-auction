# Architecture Decision Records

상태: Active 1.0
입력: [Architecture Significant Requirements](../architecture-significant-requirements.md), [Architecture Drivers](../architecture-drivers.md)
목적: ASR을 만족하기 위한 대안을 공통 기준으로 비교하고, 결정·증거·재검토 조건을 남긴다.

현재 구현은 각 ADR의 `Existing baseline` 대안일 뿐 자동으로 채택되지 않는다.

## 1. ADR Rules

1. 하나의 ADR은 하나의 decision question만 다룬다.
2. `Derived from`에 ASR과 Driver ID를 모두 적는다.
3. hard requirement를 위반하는 대안은 점수 비교 전에 제외한다.
4. 남은 대안은 같은 평가 기준과 같은 verification profile로 비교한다.
5. 예상 결과와 실제 관측 결과를 분리한다.
6. Accepted 상태에는 검증 증거와 날짜가 필요하다.
7. 요구사항을 만족하지 못하면 ADR에서 예외 처리하지 않고 요구사항 변경 여부를 먼저 판단한다.

### Status

| Status | Meaning |
|---|---|
| `Proposed` | 대안과 검증 계획을 작성했으나 결정하지 않음 |
| `Accepted` | 대안 비교와 필수 검증을 마치고 채택함 |
| `Rejected` | hard requirement 위반 또는 비교 결과로 제외함 |
| `Superseded` | 후속 ADR이 결정을 대체함 |
| `Deferred` | 현재 범위나 증거가 부족해 진입 조건까지 보류함 |

## 2. Evaluation Method

각 ADR은 먼저 hard gate를 통과해야 한다.

| Hard gate | Pass condition |
|---|---|
| Correctness | invalid/late winner 0건, deterministic result |
| Deadline | 관련 profile의 p99와 cutoff semantics 만족 |
| Fault isolation | 일부 DSP 실패가 전체 경매 실패로 번지지 않음 |
| Scope/compatibility | `C-001~006`을 위반하지 않음 |

그 후 weighted score를 계산한다.

- Weight: 1(낮음)~5(매우 높음)
- Score: 1(나쁨)~5(매우 좋음)
- Weighted score: `Σ(weight × score)`
- 점수 차이보다 근거가 중요하다. 모든 4·5점에는 실험 또는 명시적 제약 근거가 필요하다.

## 3. Decision Backlog

### `ADR-001` Auction deadline, completion, cancellation, and late response

Status: `Proposed`
Derived from: `ASR-001~003`, `AD-001~003`

Decision question:

> 여러 DSP 호출을 어떤 조건에서 종료하고, 미완료 호출과 cutoff 이후 응답을 어떻게 처리할 것인가?

Options:

| Option | Description | Key trade-off |
|---|---|---|
| A. Wait for all DSPs | 모든 호출의 terminal completion까지 대기 | completeness는 높지만 global deadline을 보장할 수 없음 |
| B. Global deadline + cancel pending | cutoff까지 완료된 결과만 수집하고 pending을 취소 | 단순하고 bounded하지만 late diagnostic이 제한될 수 있음 |
| C. Per-DSP timeout + global deadline + async late observation | DSP별 budget과 global cutoff를 함께 적용하고 사후 응답은 telemetry로만 관측 | 진단은 풍부하지만 lifecycle과 자원 회수 복잡도 증가 |
| D. Early completion | 정해진 조건에서 cutoff 전에 경매 종료 | latency는 낮지만 더 높은 후속 bid를 놓쳐 first-price 의미를 바꿀 수 있음 |

Evaluation criteria:

| Criterion | Weight | Evidence |
|---|---:|---|
| Cutoff correctness | 5 | `VP-001`, `VP-003` |
| Provider p99 | 5 | `VP-003` |
| Resource cleanup | 5 | cancellation 후 in-flight/queue |
| Fault isolation | 4 | timeout/error mix |
| Diagnostic completeness | 2 | late response metric/log |
| Implementation complexity | 2 | lifecycle state와 test surface |

Acceptance evidence required:

- `VP-003` pass
- cutoff 전후 경계 테스트
- 취소 직후와 1초 후 in-flight/queue 결과
- late response가 winner와 동기 summary를 바꾸지 않는 테스트

### `ADR-002` Concurrency, backpressure, and overload recovery

Status: `Proposed`
Derived from: `ASR-002`, `ASR-007`, `AD-001`, `AD-003`, `AD-006`

Decision question:

> DSP fan-out의 thread/task/connection 수를 어떻게 제한하고 overload를 어떤 의미로 처리할 것인가?

Options:

| Option | Description | Key trade-off |
|---|---|---|
| A. Bounded platform-thread pool + bounded queue | pool과 queue 상한, 명시적 rejection | 동작이 명확하지만 blocking 규모에 민감 |
| B. Virtual thread per task + semaphore bulkhead | lightweight task와 별도 in-flight 상한 | blocking code는 단순하지만 semaphore/connection budget이 필수 |
| C. Async client + event loop + bounded pending requests | 적은 thread로 높은 동시성 | 자원 효율은 높지만 programming/diagnostic 복잡도 증가 |
| D. Unbounded task creation | 별도 admission 없이 요청마다 fan-out | baseline은 단순하지만 `QR-004~005` hard gate를 통과하지 못함 |

Evaluation criteria:

| Criterion | Weight | Evidence |
|---|---:|---|
| Overload recovery | 5 | `VP-004` |
| Correctness under saturation | 5 | domain checks, incorrect winner |
| Stable p99 before saturation | 5 | RPS step curve |
| Bounded thread/queue/in-flight | 5 | runtime metrics |
| Rejection semantics | 4 | overload response/result classification |
| Replacement and maintenance cost | 2 | adapter boundary와 test complexity |

Acceptance evidence required:

- `VP-002`, `VP-003`, `VP-004` pass
- stable load와 first-failure point의 executor/connection/in-flight graph
- restart 없이 recovery 입증

### `ADR-003` DSP observation, classification, and candidate boundary

Status: `Proposed`
Derived from: `ASR-003~005`, `AD-002`, `AD-005`

Decision question:

> transport observation, terminal result, valid candidate를 어떤 모델과 컴포넌트 경계로 분리할 것인가?

Options:

| Option | Description | Key trade-off |
|---|---|---|
| A. Gateway returns only valid bids | transport 계층이 decode와 모든 domain validation까지 담당 | 호출자는 단순하지만 책임과 테스트가 결합됨 |
| B. Gateway observation → classifier/judge → candidate | 외부 관찰과 domain 판단을 단계별 타입으로 분리 | 모델 수는 늘지만 fault/correctness 경계가 명확 |
| C. Single union result through all stages | 하나의 상태 객체를 계속 확장 | 초기 구현은 빠르지만 invalid transition과 의미 혼합 위험 |

Evaluation criteria:

| Criterion | Weight | Evidence |
|---|---:|---|
| Invalid state prevention | 5 | compile-time/type tests와 unit tests |
| DSP fault isolation | 5 | `VP-001`, `VP-003` |
| Result explainability | 4 | terminal summary 일관성 |
| OpenRTB/domain separation | 4 | architecture/contract tests |
| Implementation complexity | 2 | model과 mapping 수 |

### Remaining backlog

| ID | Status | Derived from | Decision question | Candidates / entry condition |
|---|---|---|---|---|
| [`ADR-004`](adr-004-http-client-server-runtime.md) | Proposed | `ASR-001~003`, `ASR-005`; `AD-001`, `AD-003`, `AD-005~006` | OpenRTB HTTP/JSON 제약 안에서 어떤 client/server runtime 조합을 baseline으로 사용할 것인가? | JDK/JDK, JDK/Apache HC5, Netty, Spring 기반 조합. `ADR-002`를 먼저 결정하고 같은 profile에서 connection control, cancellation, saturation을 비교한다. |
| [`ADR-005`](adr-005-hot-path-observability.md) | Proposed | `ASR-006~007`; `AD-004`, `AD-006` | 필수 신호를 어떤 방식으로 기록·노출할 것인가? | metrics+structured log, metrics+sampled trace, full trace. `QR-007~008`과 cardinality hard gate를 적용한다. |
| `ADR-006` | Deferred | `FR-001~002`, `FR-009`, `A-001~002`; `AD-005~006` | inventory/campaign snapshot을 어떻게 교체하고 일관되게 노출할 것인가? | runtime reload/freshness가 범위에 들어오거나 in-memory fixture가 검증을 막을 때 시작한다. |

## 4. ADR Template

새 ADR은 아래 필드를 빠짐없이 사용한다.

| Field | Required content |
|---|---|
| Header | ID, title, status, date, related ADRs |
| Derived from | ASR와 Driver ID |
| Decision question | 하나의 의사결정 질문 |
| Hard requirements | 위반 시 제외되는 FR/QR/C와 pass condition |
| Options | 상태 유지안을 포함한 현실적인 대안 |
| Criteria | 공통 criterion, weight, 측정 방법 |
| Comparison | 대안별 score, 근거, 불확실성 |
| Decision | 선택과 제외한 대안의 이유 |
| Consequences | 긍정, 부정, 새 위험 |
| Verification | profile, metric, threshold, test artifact |
| Observed result | 날짜, commit, 결과 링크, 예상과의 차이 |
| Revisit trigger | 판정 가능한 수치 또는 범위 변경 조건 |

## 5. Explicitly Deferred Decisions

다음 항목은 현재 요구사항 범위 밖이므로 ADR을 만들지 않는다.

- billing/ledger/idempotent money event
- Kubernetes/autoscaling/multi-region
- advanced DSP routing
- full OpenRTB feature coverage
- user identity/privacy pipeline

범위가 바뀌면 먼저 [RTB Auction System Requirements](../../requirements/rtb-auction-system-requirements.md)를 수정한다.
