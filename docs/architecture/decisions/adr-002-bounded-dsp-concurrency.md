# ADR-002: Bounded DSP Concurrency

Status: Accepted
Verification: Partial
Date: 2026-07-10
Derived from: `ASR-002`, `ASR-007`, `AD-001`, `AD-003`, `AD-007`

## Decision Question

DSP fan-out의 task와 thread를 어떻게 제한하고 saturation을 어떻게 격리할 것인가?

## Options and Trade-offs

| Option | Boundedness | Simplicity | Expected efficiency | Diagnostic control | Result |
|---|---:|---:|---:|---:|---|
| Bounded platform-thread pool + bounded queue | 5 | 4 | 3 | 5 | Select |
| Virtual thread per call + semaphore | 5 | 4 | 4 | 3 | Revisit candidate |
| Async event loop + bounded pending | 5 | 2 | 5 | 4 | No current need justifies complexity |
| Unbounded task creation/queue | 1 | 5 | 2 | 1 | Reject: `QR-004` 위반 |

## Decision

- DSP HTTP client에 bounded `ThreadPoolExecutor`와 bounded `ArrayBlockingQueue`를 제공한다.
- pool size, max size, queue capacity, keep-alive는 환경 설정으로 조정한다.
- rejection은 DSP 호출 하나의 `ERROR`로 격리하며 다른 DSP 결과와 winner 판단을 계속한다.
- inbound JDK server는 virtual thread를 사용할 수 있지만 DSP fan-out resource budget과 동일시하지 않는다.
- active, pool, queued, completed, rejected, DSP in-flight를 노출한다.

## Consequences

- overload가 무제한 memory/thread 증가 대신 명시적 rejection으로 나타난다.
- blocking HTTP workload에서 pool 크기가 capacity와 tail latency에 직접 영향을 준다.
- queue가 너무 크면 rejection 대신 queueing latency가 deadline을 소비한다.
- connection pool을 직접 계측하지 못하는 JDK client 제약은 `ADR-004`의 revisit risk다.

## Verification

- Passed: pool+queue saturation 시 rejection test
- Passed: queue drain 후 새 작업 수락 recovery test
- Passed: executor metric binding과 architecture tests
- Pending: `VP-002~004` first-failure RPS, 30초 recovery, in-flight 0

## Revisit Trigger

- reference profile에서 queue delay가 latency의 주요 원인으로 확인됨
- bounded executor로 목표 RPS를 만족하지 못함
- connection acquisition 상태가 병목인데 JDK client에서 관측할 수 없음
