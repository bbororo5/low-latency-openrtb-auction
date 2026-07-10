# ADR-001: Global Auction Deadline

Status: Accepted
Verification: Partial
Date: 2026-07-10
Derived from: `ASR-001~003`, `AD-001~003`

## Decision Question

여러 DSP 호출을 언제 종료하고 cutoff 이후 응답과 미완료 호출을 어떻게 처리할 것인가?

## Options and Trade-offs

| Option | Deadline correctness | Resource lifecycle | Diagnostic detail | Complexity | Result |
|---|---:|---:|---:|---:|---|
| Wait for every DSP | 1 | 2 | 4 | 2 | Reject: global cutoff 위반 |
| Global deadline + cancel pending | 5 | 5 | 2 | 4 | Select |
| Per-DSP budget + global deadline + retained late observation | 5 | 3 | 5 | 2 | Defer until late diagnosis is required |
| Early completion before cutoff | 2 | 5 | 3 | 3 | Reject: 더 높은 후속 bid를 누락 |

## Decision

- `bidCutoff = receivedAt + effectiveTmax`를 하나의 global deadline으로 사용한다.
- DSP fan-out 직전에 남은 시간을 모든 DSP에 공통 `outboundTmax`로 전달한다.
- deadline이 끝났으면 DSP 호출을 시작하지 않는다.
- deadline까지 완료된 결과만 수집하고 미완료 future를 취소한다.
- cutoff 이후 관찰, client timeout, cancellation은 동기 결과에서 `TIMEOUT`이다.
- 취소 이후 응답을 관찰하기 위한 별도 async lifecycle은 유지하지 않는다.

## Consequences

- winner eligibility와 provider completion이 하나의 clock boundary를 공유한다.
- 원래 provider `tmax`보다 DSP가 받는 budget은 작을 수 있다.
- late-response 원인 분석은 제한되지만 pending 작업과 상태 공간이 작다.
- future 취소가 실제 socket work를 즉시 멈춘다는 보장은 없으므로 in-flight와 executor 회복을 측정한다.

## Verification

- Passed: cutoff 이후 bid가 timeout 하나로 분류되는 unit test
- Passed: outbound `tmax`가 남은 budget 이하인 component test
- Passed: timeout DSP가 있어도 valid winner를 유지하는 HTTP E2E
- Pending: `VP-003` p99와 `VP-004` in-flight recovery

## Revisit Trigger

- timeout 원인 분석에 실제 post-cutoff response 관측이 필수가 됨
- 취소 후 in-flight가 `QR-004`를 만족하지 못함
- DSP별 서로 다른 budget/routing이 요구사항에 들어옴
