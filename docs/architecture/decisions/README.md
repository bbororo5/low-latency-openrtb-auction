# Architecture Decision Records

ADR 작성 규칙과 전체 backlog는 [`architecture-decision-backlog.md`](architecture-decision-backlog.md)를 기준으로 한다.

이 디렉터리에는 실제 비교 작업이 시작된 ADR만 개별 파일로 둔다. 구현에 이미 사용 중인 기술이라도 대안 비교와 verification evidence가 없으면 `Accepted`로 간주하지 않는다.

## Index

| ID | Status | Record | Note |
|---|---|---|---|
| `ADR-001` | Proposed | Backlog only | Deadline, completion, cancellation, late response |
| `ADR-002` | Proposed | Backlog only | Concurrency, backpressure, overload recovery |
| `ADR-003` | Proposed | Backlog only | DSP observation, classification, candidate boundary |
| `ADR-004` | Proposed | [`adr-004-http-client-server-runtime.md`](adr-004-http-client-server-runtime.md) | Existing JDK/JDK 구현을 하나의 후보로 재평가 |
| `ADR-005` | Proposed | [`adr-005-hot-path-observability.md`](adr-005-hot-path-observability.md) | Existing Micrometer 구성을 하나의 후보로 재평가 |
| `ADR-006` | Deferred | Backlog only | Serving snapshot boundary |

현재 `Accepted` ADR은 없다. P0인 `ADR-001~003`을 먼저 검토하고, 그 결과를 `ADR-004~005`의 입력으로 사용한다.

## Migration from the previous ADR set

| Previous record | Action | Reason |
|---|---|---|
| OpenRTB-compatible HTTP transport | Deleted | HTTP/JSON과 OpenRTB subset은 선택지가 아니라 `C-001`의 hard constraint다. Deadline과 connection 관련 판단은 `ADR-001`, `ADR-004`로 분리한다. |
| JDK HttpClient for DSP gateway | Replaced by `ADR-004` | client만 고르면 server execution, connection, cancellation, backpressure와의 조합을 평가할 수 없다. |
| JDK HttpServer with Micrometer | Split into `ADR-004` and `ADR-005` | HTTP runtime과 telemetry strategy는 독립적으로 변경·재검토할 수 있는 서로 다른 질문이다. |

삭제된 문서의 이력은 Git에 남으며 새로운 ADR의 근거로 자동 승계되지 않는다.
