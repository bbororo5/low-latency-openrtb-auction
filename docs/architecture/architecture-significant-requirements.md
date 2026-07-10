# Architecture Significant Requirements

상태: Baseline 1.0
입력: [RTB Auction System Requirements](../requirements/rtb-auction-system-requirements.md)
목적: 아키텍처 구조·동시성·경계·검증 방식을 실질적으로 바꾸는 요구만 선별한다.

ASR은 설계 대안이나 선택 결과가 아니다. 이 문서는 `무엇을 만족해야 하는가`와 `어떻게 판정하는가`까지만 정의한다.

## 1. Selection Rule

요구사항이 다음 중 하나 이상에 해당하면 ASR 후보로 본다.

1. 컴포넌트, 인터페이스, 데이터 ownership, 동시성 또는 실행 구조를 바꾼다.
2. 여러 현실적인 설계 대안 사이에 품질 trade-off가 있다.
3. 실패 영향, 불확실성 또는 변경 비용이 높다.
4. 둘 이상의 컴포넌트에 걸친 전술과 검증이 필요하다.
5. reference profile로 객관적인 architecture test를 만들 수 있다.

## 2. Priority Summary

| ID | ASR | Source requirements | Importance | Risk |
|---|---|---|---:|---:|
| `ASR-001` | Deadline-bounded auction completion | `FR-003~008`, `QR-001~002`, `C-005` | High | High |
| `ASR-002` | Bounded concurrency and recovery | `FR-003`, `FR-008`, `QR-004~005` | High | High |
| `ASR-003` | Partial DSP failure isolation | `FR-004`, `FR-007~008`, `QR-003` | High | High |
| `ASR-004` | Candidate correctness and deterministic winner | `FR-004~007`, `FR-010`, `QR-006` | High | Medium |
| `ASR-005` | Stable provider/OpenRTB boundary | `FR-001~005`, `C-001~004`, `C-006` | Medium | Medium |
| `ASR-006` | Diagnostic observability with bounded cost | `QR-007~008` | High | Medium |
| `ASR-007` | Reproducible performance evidence | `G-002~003`, `QR-009`, `A-004` | High | Medium |
| `ASR-008` | Immutable startup serving snapshots | `FR-002`, `FR-009`, `A-001`, `OOS-004` | Medium | Medium |

`Importance`는 프로젝트 목표 기여도, `Risk`는 구현 실패 가능성과 영향 범위를 뜻한다. 둘 다 High인 ASR은 P0 ADR의 우선 입력이다.

## 3. Quality Scenarios

### `ASR-001` Deadline-bounded auction completion

| Element | Definition |
|---|---|
| Source | Provider client 또는 load generator |
| Stimulus | 유효 요청이 들어오고 하나 이상의 DSP가 `bidCutoff` 이후에 응답하거나 응답하지 않는다. |
| Environment | `VP-003`, observability enabled |
| Artifact | SSP web boundary, auction flow, DSP gateway, bid judge |
| Response | cutoff 이하에 도착한 응답만 후보로 삼고, cutoff 시 미완료 DSP를 `TIMEOUT`으로 확정하며, 결과를 반환한다. 사후 응답은 winner를 바꾸지 않는다. |
| Measure | late winner 0건; domain checks 100%; provider p99 ≤ 150 ms; timeout result 1/request |

Architecture impact: fan-out completion, timeout 계산, 취소, 결과 수집, clock boundary가 하나의 일관된 정책을 가져야 한다.

### `ASR-002` Bounded concurrency and recovery

| Element | Definition |
|---|---|
| Source | Load generator |
| Stimulus | arrival rate가 안정 처리 구간을 넘어 executor, connection 또는 in-flight budget을 소진한다. |
| Environment | `VP-004` overload와 이후 1 RPS recovery 구간 |
| Artifact | HTTP server execution, DSP client executor, task queue, connection management |
| Response | 신규 작업은 정의된 방식으로 제한·거절되고 기존 자원은 회수된다. 부하가 낮아지면 재시작 없이 정상 처리로 복귀한다. |
| Measure | unbounded queue/thread 없음; overload 중 incorrect winner 0건; 30초 안에 연속 30건 domain checks 100%; profile 종료 1초 후 DSP in-flight 0 |

Architecture impact: admission control, bulkhead, executor/queue, connection budget, cancellation과 overload response가 결정 대상이다.

### `ASR-003` Partial DSP failure isolation

| Element | Definition |
|---|---|
| Source | DSP endpoint |
| Stimulus | 한 DSP가 timeout, malformed response, transport error 또는 application error를 낸다. |
| Environment | 정상 부하와 `VP-003` |
| Artifact | DSP gateway, result classifier, auction flow |
| Response | 실패한 DSP만 non-candidate로 분류하고 다른 DSP의 유효 bid로 경매를 계속한다. |
| Measure | HTTP failure 0%; domain checks 100%; 유효 후보가 있으면 정확한 winner, 없으면 `NO_WINNER` |

Architecture impact: DSP별 failure boundary와 전체 경매 상태를 분리해야 한다.

### `ASR-004` Candidate correctness and deterministic winner

| Element | Definition |
|---|---|
| Source | DSP endpoints와 test fixture |
| Stimulus | valid, late, below-floor, wrong request/imp, wrong currency/media, 동가 bid가 섞여 도착한다. |
| Environment | `VP-001`, 응답 순서를 반복마다 변경 |
| Artifact | bid validation, candidate set, winner selection, result summary |
| Response | 유효한 on-time bid만 후보가 되고, first-price와 고정 tie-break로 winner를 선택한다. |
| Measure | invalid/late winner 0건; terminal result count 합 일치; 100회 반복의 winner와 price 동일 |

Architecture impact: external observation, validated candidate, winner decision 사이에 강한 타입/책임 경계가 필요하다.

### `ASR-005` Stable provider/OpenRTB boundary

| Element | Definition |
|---|---|
| Source | Provider client와 DSP implementation |
| Stimulus | provider request가 banner/video 조건을 전달하고 SSP가 DSP 요청을 생성한다. |
| Environment | `VP-001`, supported와 unsupported payload 포함 |
| Artifact | provider API, normalization, OpenRTB codec/model |
| Response | provider 계약을 OpenRTB 계약과 분리하고, one-imp/USD/first-price subset만 DSP 경계에 노출한다. |
| Measure | supported contract tests 100%; unsupported 요청은 fan-out 전 거절; context와 BidRequest 핵심 필드 일치 |

Architecture impact: 외부 입력 모델, 내부 auction context, OpenRTB wire model을 하나의 범용 객체로 합치지 않는다.

### `ASR-008` Immutable startup serving snapshots

| Element | Definition |
|---|---|
| Source | SSP/DSP process bootstrap |
| Stimulus | inventory 또는 campaign serving data를 process에 제공하고 요청 처리를 시작한다. |
| Environment | process lifetime, runtime reload disabled |
| Artifact | inventory catalog, campaign lookup, request/bid handling |
| Response | 입력 값을 immutable snapshot으로 복사하고 모든 hot-path read를 local snapshot에서 수행한다. 변경은 재시작 후에만 보인다. |
| Measure | hot-path external data call 0건; 생성 이후 collection mutation 불가; 동일 process에서 동일 key의 serving view가 바뀌지 않음 |

Architecture impact: 원본 데이터 관리와 serving read model을 분리하고, 현재 범위에는 reload protocol이나 분산 일관성을 도입하지 않는다.

### `ASR-006` Diagnostic observability with bounded cost

| Element | Definition |
|---|---|
| Source | 개발자 또는 성능 실험 |
| Stimulus | latency 증가, timeout 증가, executor rejection 또는 잘못된 outcome이 관찰된다. |
| Environment | `VP-002~004` |
| Artifact | metrics, structured logs, trace/correlation boundary |
| Response | auction과 DSP call latency/result, in-flight, executor active/queued/rejected로 병목 구간과 실패 종류를 구분한다. |
| Measure | `QR-007` signal 조회 가능; high-cardinality metric tag 0개; 관측 on/off p99 차이 ≤ 10% |

Architecture impact: hot path instrumentation, metric cardinality, 비동기 기록 여부와 exporter 경계를 결정해야 한다.

### `ASR-007` Reproducible performance evidence

| Element | Definition |
|---|---|
| Source | 개발자 또는 CI/performance runner |
| Stimulus | 변경 전후 성능이나 두 아키텍처 대안을 비교한다. |
| Environment | 고정된 reference environment와 동일한 verification profile |
| Artifact | load generator, target deployment, performance report |
| Response | 같은 입력·topology·duration·관측 조건으로 반복하고 도메인 정확성과 시스템 지표를 함께 기록한다. |
| Measure | `QR-009` 필드 누락 0개; client dropped iteration 0; 대안 비교 시 동일 profile 사용 |

Architecture impact: load generator를 target 자원과 분리하고, 실험 구성과 결과를 versioned artifact로 유지해야 한다.

## 4. Verification Ownership

| Area | Required verification |
|---|---|
| Domain tests | candidate validation, terminal classification, no-winner, tie-break |
| Component tests | deadline calculation, DSP cancellation/result collection, overload rejection |
| Contract tests | provider input, OpenRTB subset, HTTP status/error mapping |
| Load tests | `VP-002`, `VP-003` |
| Stress/recovery tests | `VP-004` |
| Architecture tests | provider/domain/OpenRTB model boundary, snapshot read boundary와 의존 방향 |

후속 ADR은 각 ASR의 `Response`와 `Measure`를 평가 기준으로 사용해야 한다.
