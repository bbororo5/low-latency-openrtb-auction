# Architecture Decision Register

상태: Active 1.0
입력: [ASR](../architecture-significant-requirements.md), [Architecture Drivers](../architecture-drivers.md)

ADR status는 선택 여부를, verification status는 구현과 증거 수준을 나타낸다. `Accepted`는 현재 baseline에 적용할 결정을 뜻하며 모든 성능 주장이 이미 검증됐다는 뜻이 아니다.

## Decision Status

| Status | Meaning |
|---|---|
| `Proposed` | 대안과 질문은 있으나 선택하지 않음 |
| `Accepted` | 현재 baseline에 적용할 대안을 선택함 |
| `Superseded` | 후속 ADR이 대체함 |
| `Deferred` | 현재 범위 밖이며 진입 조건을 기록함 |

## Verification Status

| Status | Meaning |
|---|---|
| `Verified` | 명시한 자동 또는 실험 검증을 통과함 |
| `Partial` | 기능/구조 검증은 통과했으나 reference profile이 남음 |
| `Pending` | 구현 또는 필수 증거가 아직 없음 |

## Register

| ID | Decision | Status | Verification | Record |
|---|---|---|---|---|
| `ADR-001` | Global deadline, cancel pending, no retained late lifecycle | Accepted | Partial | [Global Auction Deadline](adr-001-global-auction-deadline.md) |
| `ADR-002` | Bounded DSP executor and queue | Accepted | Partial | [Bounded DSP Concurrency](adr-002-bounded-dsp-concurrency.md) |
| `ADR-003` | Observation → classification → candidate boundary | Accepted | Verified | [DSP Result Classification](adr-003-dsp-result-classification.md) |
| `ADR-004` | JDK server/client runtime baseline | Accepted | Partial | [HTTP Client and Server Runtime](adr-004-http-client-server-runtime.md) |
| `ADR-005` | Micrometer aggregate metrics baseline | Accepted | Partial | [Hot-Path Observability](adr-005-hot-path-observability.md) |
| `ADR-006` | Immutable startup serving snapshots | Accepted | Verified for current scope | [Startup Serving Snapshots](adr-006-startup-serving-snapshots.md) |

Reference 성능 환경의 `VP-002~004` 결과가 없으므로 latency, capacity, overload recovery와 observability overhead는 아직 검증 완료로 표시하지 않는다.

## ADR Rule

1. 하나의 ADR은 하나의 decision question을 다룬다.
2. ASR과 Driver를 모두 참조한다.
3. hard requirement를 위반하는 대안은 제외한다.
4. 선택은 현실적인 대안, 현재 제약, 구현 비용, 사용 가능한 증거로 설명한다.
5. 미검증 주장은 verification status와 후속 profile로 노출한다.
6. 요구사항 변경이 필요하면 ADR에서 예외를 만들지 않고 requirements부터 수정한다.
7. 범위·측정 결과가 revisit trigger를 만족하면 기존 ADR을 수정하지 않고 superseding ADR을 만든다.

## Explicitly Deferred Topics

- billing/ledger/idempotent money event
- runtime snapshot distribution and freshness SLA
- Kubernetes/autoscaling/multi-region
- advanced DSP routing
- full OpenRTB feature coverage
- user identity/privacy pipeline

이 항목이 필요해지면 먼저 requirements와 concern coverage를 변경한다.
