# Architecture Drivers

상태: Baseline 1.0
입력: [RTB Auction System Requirements](../requirements/rtb-auction-system-requirements.md)
목적: 여러 요구·제약·위험을 설계를 움직이는 소수의 force로 묶고 우선순위를 정한다.

Driver는 품질속성 이름이나 설계 결론이 아니다. `어떤 힘이 어떤 결정에 압력을 주며 무엇과 충돌하는가`를 설명한다. ASR과 같은 요구사항을 참조할 수 있지만 ASR에서 파생되지는 않는다.

## 1. Ranked Drivers

| Rank | ID | Driver | Requirement sources | Related ASR |
|---:|---|---|---|---|
| 1 | `AD-001` | Bounded-deadline multi-DSP fan-out | `G-001`, `FR-003~008`, `QR-001~002`, `C-005` | `ASR-001` |
| 2 | `AD-002` | Correct auction under unreliable DSP behavior | `G-003`, `FR-004~008`, `QR-003`, `QR-006` | `ASR-003~004` |
| 3 | `AD-003` | Bounded resources and recovery after overload | `G-002`, `QR-004~005` | `ASR-002` |
| 4 | `AD-004` | Measurable and reproducible performance evidence | `G-002~003`, `QR-007~009`, `A-004` | `ASR-006~007` |
| 5 | `AD-005` | OpenRTB compatibility within a deliberately small scope | `FR-001~005`, `C-001~006`, `OOS-001~007` | `ASR-005` |
| 6 | `AD-006` | Low replacement cost for an experimental baseline | `G-002~003`, `C-003`, `A-001~003` | `ASR-002`, `ASR-006~007` |

Rank는 ADR에서 충돌하는 기준의 기본 우선순위다. 하위 Driver가 상위 Driver를 침해하는 결정을 정당화하지 못한다.

## 2. Driver Records

| ID | Force | Primary tension | Decisions pressured | Required evidence |
|---|---|---|---|---|
| `AD-001` | 한 요청이 여러 외부 호출로 증폭되고, 응답 가치보다 `bidCutoff` 준수가 우선한다. | bid completeness vs deadline; 단순 대기 vs 정확한 cancellation lifecycle | global/per-DSP timeout, completion condition, pending cancellation, late observation, clock propagation | `VP-003` p99, timeout count, late winner 0, cancellation 후 in-flight |
| `AD-002` | DSP는 bid 외에 no-bid, timeout, malformed response, error를 내며 외부 응답은 검증 전까지 후보가 아니다. | fail-fast simplicity vs DSP별 isolation; 도착 순서 편의 vs deterministic result | observation/candidate boundary, terminal taxonomy, error mapping, tie-break | `VP-001`, `VP-003` domain checks 100%와 순서 변경 테스트 |
| `AD-003` | fan-out은 thread, task, connection, memory를 증폭시키며 overload 후 재시작이 필요한 상태는 허용할 수 없다. | 순간 throughput vs bounded resource/recovery; 큰 queue vs queueing latency | executor model, queue/connection/in-flight budget, admission control, overload semantics | `VP-004` saturation, rejection, resource curve, 30초 recovery |
| `AD-004` | 이 프로젝트는 구현뿐 아니라 병목을 설명하는 재현 가능한 증거가 산출물이다. | instrumentation detail vs hot-path cost; 편의 vs 환경 격리 | signal set, cardinality, load-generator 배치, measurement layers, result format | `VP-002~004`의 versioned environment/workload/domain/system result |
| `AD-005` | SSP-DSP 경계는 OpenRTB 의미를 유지하되 전체 표준 구현은 학습 범위를 압도한다. | compatibility vs optimization; broad coverage vs one-imp focus; model reuse vs boundary clarity | explicit subset, provider/domain/wire model 분리, unsupported handling, codec/runtime replacement | `ASR-005` contract tests와 pre-fan-out rejection |
| `AD-006` | 단일 개발자가 baseline을 반복 교체·비교하므로 기술 선택이 domain policy와 강결합되면 안 된다. | minimal dependency vs operability; 빠른 구현 vs fine control | adapter boundary, library adoption, experiment configuration, revisit trigger | 같은 domain/load contract를 유지한 대안 교체 가능성 |

## 3. Trade-off Order

| Tension | Default priority | ADR must prove |
|---|---|---|
| Completeness vs deadline | Deadline | 제외된 응답이 결과 정확성을 훼손하지 않음 |
| Throughput vs bounded resources | Correctness와 recovery | 높은 RPS가 지속 실패를 만들지 않음 |
| Simplicity vs fault isolation | Fault isolation | 일부 DSP 실패가 전체 실패로 전파되지 않음 |
| Observability vs latency | Deadline, then explainability | 관측 overhead가 `QR-008` 안에 있음 |
| Compatibility vs optimized transport | OpenRTB boundary | 최적화가 `C-001`을 깨지 않음 |
| Minimal stack vs operability | Evidence quality | 단순성 때문에 saturation signal이 사라지지 않음 |

## 4. Driver-to-Decision Map

| Decision topic | Primary drivers |
|---|---|
| Deadline, completion, cancellation, late response | `AD-001`, `AD-002`, `AD-003` |
| Executor, backpressure, overload recovery | `AD-001`, `AD-003`, `AD-006` |
| DSP result/candidate boundary | `AD-002`, `AD-005` |
| HTTP client/server/runtime stack | `AD-001`, `AD-003`, `AD-005`, `AD-006` |
| Metrics/logs/traces | `AD-004`, `AD-001`, `AD-003` |
| Serving snapshot boundary | `AD-005`, `AD-006` |

최종 선택은 이 문서에 기록하지 않고 ADR에서 비교한다.
