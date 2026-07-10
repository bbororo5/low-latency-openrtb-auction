# Architecture Concern Coverage

상태: Active 1.0
목적: 새로운 관점이 뒤늦게 별도 단계로 추가되지 않도록, 아키텍처 검토 범위를 고정한다.

관심사는 문서 흐름의 중간 단계가 아니다. 각 관심사는 기존 요구사항, ASR/Driver, ADR, 아키텍처 설명, 검증 증거 중 어디에서 다루는지 지정한다. 새 관심사가 발견되면 먼저 이 표에 추가하고 기존 산출물에 배치하며, 별도 문서는 실제 결정이나 구현 계약이 있을 때만 만든다.

## Coverage Matrix

| Concern | Status | Current owner | Remaining verification or trigger |
|---|---|---|---|
| Scope and domain semantics | Covered | [Requirements](../requirements/rtb-auction-system-requirements.md) | 범위 변경 시 재검토 |
| Time, deadline, concurrency | Covered | `ASR-001~002`, `AD-001~003`, `ADR-001~002` | `VP-003~004` reference evidence |
| DSP failure and result correctness | Covered | `ASR-003~004`, `ADR-003` | fault-mix E2E와 `VP-003` |
| Provider/OpenRTB interface semantics | Covered | [Interface Contracts](interface-contracts.md), `ADR-001`, `ADR-003` | 외부 연동 범위 진입 시 contract certification |
| Domain and serving data | Covered for current scope | [Data Architecture](data-architecture.md), `ASR-008`, `ADR-006` | runtime reload/freshness가 범위에 들어오면 재설계 |
| Runtime resources and overload | Covered, evidence pending | [System Architecture](system-architecture.md), `ADR-002`, `ADR-004` | `VP-004` reference evidence |
| Observability and operability | Covered, evidence pending | `ASR-006~007`, `ADR-005` | `QR-008` overhead comparison |
| Deployment and topology | Covered for baseline | [System Architecture](system-architecture.md), `C-003`, `A-003~004` | multi-region/autoscaling은 `OOS-006` |
| Security and privacy | N/A for current release | `OOS-002`, `OOS-005`, private benchmark assumption | identity, public internet, TLS가 범위에 들어오면 요구사항부터 변경 |
| Evolution and replacement | Covered | `AD-007`, ADR revisit triggers | protocol/media/runtime 범위 변경 |
| Verification and evidence provenance | Partially covered | `ASR-007`, `QR-009`, [Performance Evidence](../evidence/performance/) | reference environment 실행 결과 없음 |

## Fixed Review Rule

요구사항 또는 ADR을 변경할 때 다음 순서만 사용한다.

1. 이 표에서 영향을 받는 concern을 찾는다.
2. 요구사항의 의미나 합격 기준이 바뀌면 requirements부터 수정한다.
3. ASR과 Driver의 우선순위·압력이 바뀌는지 확인한다.
4. 대안 선택이 바뀌면 ADR을 추가하거나 supersede한다.
5. 구조·인터페이스·데이터 계약을 architecture description에 반영한다.
6. 구현과 evidence가 새 계약을 만족하는지 확인한다.

새로운 기술적 견해만으로 문서 단계를 추가하지 않는다.
