# ADR-004: HTTP Client and Server Runtime

Status: Proposed
Date: 2026-07-10
Derived from: `ASR-001`, `ASR-002`, `ASR-003`, `ASR-005`; `AD-001`, `AD-003`, `AD-005`, `AD-006`
Related: `ADR-001`, `ADR-002`

## Decision Question

OpenRTB HTTP/JSON 제약 안에서 어떤 HTTP client/server runtime 조합을 baseline으로 사용할 것인가?

## Why This Is Not Accepted Yet

현재 구현은 다음 조합을 사용한다.

- server: JDK `HttpServer` + virtual-thread-per-task executor
- DSP client: JDK `HttpClient` HTTP/1.1 + bounded platform-thread executor
- shared client instance와 persistent connection reuse
- auction deadline에서 계산한 request timeout

이 조합은 실행 가능한 baseline이지만 다음 증거가 없다.

- 동일 verification profile에서 다른 client/server 조합과 비교한 결과
- `ADR-002`에서 정할 concurrency/backpressure 정책과의 정합성
- `VP-004` overload 후 30초 recovery 통과 결과

따라서 기존 구현을 유지해 측정할 수는 있지만 아키텍처 결정으로 채택하지 않는다.

## Hard Requirements

| Requirement | Pass condition |
|---|---|
| `QR-001`, `ASR-001` | cutoff 이후 응답이 winner가 되지 않는다. |
| `QR-002` | `VP-002` p99 ≤ 120 ms, `VP-003` p99 ≤ 150 ms |
| `QR-003`, `ASR-003` | 일부 DSP 실패 시 HTTP failure 0%, domain checks 100% |
| `QR-004~005`, `ASR-002` | resource bound가 있고 `VP-004`에서 재시작 없이 회복한다. |
| `C-001`, `ASR-005` | OpenRTB 2.6 subset과 HTTP/JSON boundary를 유지한다. |

Hard requirement를 통과하지 못한 조합은 weighted comparison에서 제외한다.

## Options

| Option | Server | DSP client | Expected advantage | Primary risk |
|---|---|---|---|---|
| A. Existing JDK baseline | JDK HttpServer + virtual threads | JDK HttpClient + bounded executor | Java 21 표준 API, 낮은 도입 비용 | pool/connection 세부 제어와 saturation 진단 제한 |
| B. JDK server + Apache client | JDK HttpServer + virtual threads | Apache HttpClient 5 async | connection pool과 timeout 제어 강화 | 두 실행 모델을 함께 운영하는 복잡도 |
| C. Netty stack | Netty | Netty client | 일관된 event-loop와 세밀한 network control | 구현·운영·진단 복잡도 증가 |
| D. Framework server + async client | Spring Boot 계열 | 검증된 async client | health/metrics/config 운영 편의 | baseline overhead와 framework 영향 분리 필요 |

## Evaluation Criteria

| Criterion | Weight | Evidence |
|---|---:|---|
| Domain correctness and fault isolation | 5 | `VP-001`, `VP-003` |
| Provider p99 | 5 | `VP-002`, `VP-003` |
| Bounded resource and recovery | 5 | `VP-004`, thread/queue/in-flight/connection |
| Cancellation and timeout control | 5 | cutoff boundary와 pending-call test |
| Saturation visibility | 4 | active/queued/rejected/acquisition metrics |
| Replacement and maintenance cost | 2 | adapter 변경 범위와 test surface |

## Comparison

아직 점수를 부여하지 않는다. `ADR-002`가 executor, queue, admission control을 먼저 결정해야 client/server 조합을 같은 resource policy로 비교할 수 있다.

## Decision

결정하지 않음. Option A를 existing baseline으로 유지하되 Accepted로 보지 않는다.

## Expected Consequences

- 장점: 구현을 중단하지 않고 측정과 대안 준비를 계속할 수 있다.
- 단점: 결정 전까지 JDK stack에 맞춘 최적화를 장기 구조로 간주할 수 없다.
- 위험: baseline에만 계측 가능한 신호를 사용하면 다른 대안과의 비교가 왜곡될 수 있다.

## Verification Plan

1. `ADR-001`, `ADR-002`를 먼저 결정한다.
2. 각 surviving option에 같은 deadline, resource budget, topology를 적용한다.
3. `VP-001~004`를 실행한다.
4. p95/p99, first-failure RPS, thread/queue/in-flight/connection, recovery time을 비교한다.

## Observed Result

아직 없음. 기존 performance report는 문제 발견의 증거로 사용할 수 있지만 동일 조건의 대안 비교 결과는 아니다.

## Revisit Trigger

- `ADR-001` 또는 `ADR-002`가 Accepted가 됨
- existing baseline이 `VP-002`, `VP-003`, `VP-004` 중 하나를 통과하지 못함
- 다른 option의 동일-profile 비교 결과가 준비됨
