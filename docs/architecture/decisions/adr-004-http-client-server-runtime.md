# ADR-004: JDK HTTP Client and Server Runtime

Status: Accepted
Verification: Partial
Date: 2026-07-10
Derived from: `ASR-001~003`, `ASR-005`; `AD-001`, `AD-003`, `AD-005`, `AD-007`
Related: `ADR-001`, `ADR-002`

## Decision Question

OpenRTB HTTP/JSON baseline에 어떤 server/client runtime을 사용할 것인가?

## Options and Trade-offs

| Option | Control | Dependency/maintenance cost | Operability | Current evidence | Result |
|---|---:|---:|---:|---:|---|
| JDK `HttpServer` + JDK `HttpClient` | 3 | 5 | 3 | Existing tests and historical diagnostics | Select |
| JDK server + Apache HC5 async | 4 | 3 | 4 | Not implemented | Revisit candidate |
| Netty server/client | 5 | 2 | 4 | Not implemented | No current need justifies complexity |
| Spring/framework stack | 3 | 2 | 5 | Not implemented | Baseline overhead is unnecessary now |

## Decision

- server는 JDK `HttpServer`와 virtual-thread-per-exchange executor를 사용한다.
- DSP client는 shared JDK `HttpClient`, HTTP/1.1, bounded executor를 사용한다.
- adapter와 gateway interface 뒤에 두어 domain policy가 runtime API를 직접 참조하지 않게 한다.
- 대안 전체를 구현해 비교하지 않는다. 현재 hard requirement를 실패하거나 JDK 제어 한계가 측정될 때만 다음 후보를 실험한다.

## Consequences

- Java 21 표준 API만으로 작고 교체 가능한 baseline을 유지한다.
- connection pool/acquisition에 대한 세밀한 제어와 metric은 제한된다.
- JDK runtime 성능이 reference profile을 통과할지는 별도 검증 문제다.

## Verification

- Passed: provider→SSP→DSP HTTP E2E
- Passed: timeout/fault isolation component tests
- Pending: `VP-002~004` reference latency, saturation, recovery

## Revisit Trigger

- `VP-002~004` 실패 원인이 JDK server/client 제어 한계로 확인됨
- connection acquisition 또는 protocol 기능을 직접 제어해야 함
