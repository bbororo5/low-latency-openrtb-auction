# ADR-005: Hot-Path Observability

Status: Proposed
Date: 2026-07-10
Derived from: `ASR-006`, `ASR-007`; `AD-004`, `AD-006`
Related: `ADR-001`, `ADR-002`, `ADR-004`

## Decision Question

경매 결과 오류와 latency/resource 병목을 설명하는 최소 신호를 어떤 방식으로 기록하고 노출할 것인가?

## Why This Is Not Accepted Yet

현재 구현은 Micrometer Prometheus registry로 auction/DSP latency와 result, runtime/executor signal을 노출한다. Prometheus와 Grafana를 연결할 수 있지만 다음이 검증되지 않았다.

- `QR-007`의 필수 신호 전체가 실제로 조회되는지
- 관측 on/off p99 차이가 `QR-008`의 10% 이내인지
- post-cutoff `LATE_RESPONSE`와 개별 요청 원인을 어떤 log/trace로 연결할지

따라서 Micrometer는 existing baseline option이며 Accepted decision이 아니다.

## Hard Requirements

| Requirement | Pass condition |
|---|---|
| `QR-007` | auction/DSP latency와 result, in-flight, executor active/queued/rejected를 조회할 수 있다. |
| `QR-008` | observability on/off p99 차이 ≤ 10% |
| `QR-009`, `ASR-007` | 결과에 commit, environment, topology, workload, domain/system signal이 포함된다. |
| Cardinality | request, impression, bid, campaign, creative ID를 metric tag로 사용하지 않는다. |
| Correctness | telemetry 장애나 sampling이 AuctionResult를 바꾸지 않는다. |

## Options

| Option | Description | Expected advantage | Primary risk |
|---|---|---|---|
| A. Custom minimal metrics | 직접 counter/histogram과 exposition 구현 | 가장 작은 의존성과 명시적 비용 | format, histogram, lifecycle 유지보수 부담 |
| B. Micrometer metrics + structured diagnostic log | 필수 집계는 metrics, 개별 원인은 log | 기존 baseline을 활용하고 cardinality를 분리 | log correlation과 비동기 기록 설계 필요 |
| C. Micrometer metrics + sampled trace | metrics에 SSP→DSP sampled trace 추가 | 병목 경로 확인 용이 | sampling 정책과 trace overhead |
| D. Full trace for every request | 모든 요청의 전체 call flow 기록 | 가장 상세한 진단 | `QR-008` 위반과 저장 비용 위험 |

## Evaluation Criteria

| Criterion | Weight | Evidence |
|---|---:|---|
| Required signal coverage | 5 | `QR-007` signal checklist |
| Hot-path overhead | 5 | observability on/off `VP-002`, `VP-003` |
| Cardinality safety | 5 | metric schema inspection |
| Overload diagnosis | 4 | `VP-004` saturation/recovery explanation |
| Request-level diagnosis | 3 | failure fixture correlation |
| Maintenance cost | 2 | custom code와 operational surface |

## Comparison

아직 점수를 부여하지 않는다. Option B가 existing baseline에 가장 가깝지만 `LATE_RESPONSE`, executor saturation, overhead 검증이 끝나지 않았다.

## Decision

결정하지 않음. Option B 형태의 existing baseline을 유지해 evidence를 수집한다.

## Expected Consequences

- 장점: 현재 metric과 dashboard 자산을 계속 활용할 수 있다.
- 단점: Accepted 전까지 trace 미도입이나 log 부족을 의도된 최종 구조로 해석할 수 없다.
- 위험: 관측 대상 자체가 누락되면 낮은 overhead가 좋은 대안처럼 보일 수 있다.

## Verification Plan

1. `QR-007` signal checklist를 자동 또는 수동 검증한다.
2. 동일 target과 workload에서 observability on/off로 `VP-002`, `VP-003`을 반복한다.
3. `VP-004`의 최초 포화 원인을 필수 signal만으로 설명한다.
4. timeout 후 late response fixture로 metric/log correlation을 검증한다.

## Observed Result

아직 없음. 기존 dashboard와 report는 baseline 자산이지만 10% overhead 비교 증거는 아니다.

## Revisit Trigger

- 필수 signal checklist 또는 overhead 비교 결과가 준비됨
- `ADR-001`, `ADR-002`가 새로운 lifecycle/saturation signal을 요구함
- request-level 원인 분석이 metrics와 structured log만으로 불가능함
