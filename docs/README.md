# Documentation Map

이 디렉터리의 문서는 관심사별 기준 문서로 나눈다. 같은 결정을 여러 문서에서 반복하지 않고, 각 문서가 자기 책임만 갖도록 유지한다.

| Document | Responsibility |
|---|---|
| `PRD.md` | 문제, 목표 사용자, 기능 요구사항, 성공 기준 |
| `ASR.md` | 아키텍처에 큰 영향을 주는 요구사항, 품질속성, 측정 가능한 시나리오 |
| `ARCHITECTURE.md` | 시스템 경계, C1/C2 view, 핵심 런타임 구조와 rationale |
| `API_SPEC.md` | provider-facing API, SSP-DSP OpenRTB subset, 응답/실패 계약 |
| `DATA_STATE_ARCHITECTURE.md` | 데이터 상태, source of truth, serving copy, 정합성, 이벤트/원장 후보 |
| `TECH_SPEC.md` | 구현 컴포넌트, 패키지 경계, 내부 책임, 테스트 전략 |
| `OBSERVABILITY.md` | metrics, traces, dashboards, 운영 관측 구성 |
| `PERFORMANCE_BASELINE.md` | 성능 측정 결과와 해석 |
| `adr/*` | 중요한 기술 결정의 배경, 선택지, 결과 |

문서 변경 기준:

- 새 기능 요구는 먼저 `PRD.md` 또는 `ASR.md`에서 의미를 확인한다.
- 구조 결정은 `ARCHITECTURE.md`에 남기고, 선택지가 있었던 결정은 ADR로 분리한다.
- API 필드와 상태 코드는 `API_SPEC.md`에 둔다.
- DB, Redis/Valkey, event, ledger, idempotency 논의는 `DATA_STATE_ARCHITECTURE.md`에서 시작한다.
- 클래스, 패키지, 컴포넌트 책임, 테스트 전략은 `TECH_SPEC.md`에 둔다.
