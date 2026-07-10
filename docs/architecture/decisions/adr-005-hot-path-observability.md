# ADR-005: Aggregate Hot-Path Observability

Status: Accepted
Verification: Partial
Date: 2026-07-10
Derived from: `ASR-006~007`; `AD-004`, `AD-007`

## Decision Question

경매 오류와 latency/resource 병목을 설명하는 최소 신호를 어떻게 기록하고 노출할 것인가?

## Options and Trade-offs

| Option | Signal coverage | Expected overhead | Request diagnosis | Maintenance | Result |
|---|---:|---:|---:|---:|---|
| Custom metrics | 3 | 5 | 1 | 2 | Reject: exposition/histogram 유지비 |
| Micrometer aggregate metrics | 5 | 4 | 2 | 5 | Select |
| Metrics + sampled trace | 5 | 3 | 4 | 3 | Revisit candidate |
| Full trace every request | 5 | 1 | 5 | 2 | Reject for current hot path |

## Decision

- Micrometer Prometheus registry로 auction/DSP latency와 result를 기록한다.
- gateway observation과 judge가 확정한 DSP terminal result를 별도 metric으로 기록한다.
- DSP in-flight와 executor active/pool/queued/completed/rejected, JVM runtime 신호를 노출한다.
- request, impression, bid, campaign, creative ID를 metric tag로 사용하지 않는다.
- trace는 기본 구성에 넣지 않는다. 개별 상관관계가 필수인 장애가 확인되면 sampled trace를 재검토한다.

## Consequences

- 고정 cardinality로 capacity와 saturation을 비교할 수 있다.
- 개별 요청의 post-cutoff lifecycle을 metrics만으로 재구성할 수 없다.
- instrumentation overhead는 아직 수치로 검증되지 않았다.

## Verification

- Passed: metric 이름/tag schema와 executor binding tests/inspection
- Pending: observability on/off p99 차이 `QR-008` ≤ 10%
- Pending: `VP-004` 병목 설명 가능 여부

## Revisit Trigger

- aggregate metric만으로 반복 장애의 원인을 분리할 수 없음
- `QR-008` overhead 기준 실패
