# Architecture Decision Records

전체 상태와 유지 규칙은 [Architecture Decision Register](architecture-decision-register.md)를 기준으로 한다.

| ID | Status | Verification | Record |
|---|---|---|---|
| `ADR-001` | Accepted | Partial | [Global Auction Deadline](adr-001-global-auction-deadline.md) |
| `ADR-002` | Accepted | Partial | [Bounded DSP Concurrency](adr-002-bounded-dsp-concurrency.md) |
| `ADR-003` | Accepted | Verified | [DSP Result Classification](adr-003-dsp-result-classification.md) |
| `ADR-004` | Accepted | Partial | [JDK HTTP Runtime](adr-004-http-client-server-runtime.md) |
| `ADR-005` | Accepted | Partial | [Aggregate Observability](adr-005-hot-path-observability.md) |
| `ADR-006` | Accepted | Verified for current scope | [Startup Serving Snapshots](adr-006-startup-serving-snapshots.md) |

`Accepted`와 `Verified`를 혼동하지 않는다. 남은 reference 성능 검증은 [Performance Evidence](../../evidence/performance/)에 기록한다.
