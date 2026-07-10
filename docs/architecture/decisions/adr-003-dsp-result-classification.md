# ADR-003: DSP Result Classification Boundary

Status: Accepted
Verification: Verified
Date: 2026-07-10
Derived from: `ASR-003~005`, `AD-002`, `AD-005`

## Decision Question

DSP transport observation, terminal classification, valid candidate를 어떤 책임과 타입 경계로 분리할 것인가?

## Options and Trade-offs

| Option | Invalid-state prevention | Fault isolation | Explainability | Complexity | Result |
|---|---:|---:|---:|---:|---|
| Gateway returns valid bids only | 2 | 3 | 2 | 4 | Reject: transport와 domain policy 결합 |
| Observation → judge → candidate | 5 | 5 | 5 | 3 | Select |
| One union result through every stage | 2 | 3 | 3 | 4 initially | Reject: 중복/모순 상태 허용 |

## Decision

- gateway는 `BID_RECEIVED`, `NO_BID`, `TIMEOUT`, `INVALID_RESPONSE`, `ERROR` observation을 반환한다.
- judge는 request/imp/currency/floor/media/markup/deadline을 검증한다.
- 호출한 DSP마다 정확히 하나의 `VALID_BID`, `NO_BID`, `TIMEOUT`, `INVALID_BID`, `ERROR` terminal result를 집계한다.
- terminal result가 `VALID_BID`인 응답의 유효 bid만 `ValidBidCandidate`가 된다.
- winner selector는 candidate만 입력받고 transport 상태를 해석하지 않는다.

## Consequences

- summary 합계가 실제 시작된 DSP call 수와 일치한다.
- 한 DSP 응답에 여러 bid가 있어도 DSP terminal result는 하나다.
- wire decode 실패와 domain validation 실패는 provider summary에서 모두 `INVALID_BID`로 보이지만 내부 observation은 구분할 수 있다.
- 모델 수는 늘지만 각 단계의 테스트와 책임이 작아진다.

## Verification

- Passed: 다섯 terminal category의 상호 배타성과 합계 test
- Passed: invalid request/imp/currency/media/markup tests
- Passed: deterministic winner와 partial timeout HTTP E2E

## Revisit Trigger

- provider 계약이 wire-malformed와 semantic-invalid를 별도 결과로 요구함
- multi-seat/multi-imp에서 DSP 단위 terminal result만으로 설명할 수 없음
