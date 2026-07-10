# ADR-006: Immutable Startup Serving Snapshots

Status: Accepted
Verification: Verified for current scope
Date: 2026-07-10
Derived from: `ASR-008`, `AD-005~007`

## Decision Question

원본 데이터 관리가 범위 밖인 현재 release에서 inventory와 campaign data를 hot path에 어떻게 노출할 것인가?

## Options and Trade-offs

| Option | Read latency | Freshness | State complexity | Scope fit | Result |
|---|---:|---:|---:|---:|---|
| Request-time source lookup | 1 | 5 | 3 | 1 | Reject: hot path 외부 종속성 |
| Immutable startup snapshot, restart-only update | 5 | 2 | 5 | 5 | Select |
| Atomic runtime snapshot swap | 5 | 4 | 3 | 3 | Defer until reload requirement |
| Distributed cache/streaming materialization | 4 | 5 | 1 | 1 | Reject as out of scope |

## Decision

- bootstrap에서 받은 inventory/campaign collection을 immutable collection으로 복사한다.
- process lifetime에는 snapshot을 교체하지 않는다.
- hot path는 local snapshot만 읽고 원본 저장소를 호출하지 않는다.
- 데이터 변경은 새 process startup으로 반영한다.
- snapshot version, freshness SLA, reload/rollback protocol은 정의하지 않는다.

## Consequences

- 요청 중 data view가 바뀌지 않고 read latency와 failure surface가 작다.
- 데이터 변경에는 restart가 필요하고 서로 다른 process가 다른 version을 실행할 수 있다.
- 현재 bootstrap fixture는 authoritative source가 아니라 준비된 serving input이다.

## Verification

- Passed: inventory는 `Map.copyOf`, campaign은 `List.copyOf`로 보관
- Passed: request path에 외부 data client dependency 없음
- Passed: architecture dependency tests

## Revisit Trigger

- 무중단 reload, freshness SLA, version exposure 중 하나가 요구됨
- 여러 process의 동시 snapshot 전환이 correctness에 필요함
