# RTB Architecture Documentation

상태: Active 2.0
목적: 요구사항, 설계 압력, 결정, 실제 구조, 구현 검증을 하나의 추적 가능한 체계로 유지한다.

## Directory Structure

```text
docs/
├── requirements/
│   └── rtb-auction-system-requirements.md
├── architecture/
│   ├── concern-coverage.md
│   ├── architecture-significant-requirements.md
│   ├── architecture-drivers.md
│   ├── system-architecture.md
│   ├── data-architecture.md
│   ├── interface-contracts.md
│   └── decisions/
│       ├── README.md
│       ├── architecture-decision-register.md
│       └── adr-NNN-*.md
└── evidence/
    └── performance/
        ├── README.md
        └── YYYY-MM-DD-*.md
```

## Architecture Workflow

```text
Concern Coverage
      ↓
Development Requirements
      ↓
Architecture-Significant Requirements
      ↓
Architecture Drivers
      ↓
ADR  ↔  Architecture Description
      ↓
Implementation  ↔  Verification Evidence
      └────────────── feedback to requirements/decisions
```

- ASR은 검증 가능한 아키텍처 중요 요구사항이다.
- Architecture Driver는 목표·ASR·제약·위험을 우선순위가 있는 설계 압력으로 종합한다.
- ADR은 대안과 trade-off, 선택, 결과, 재검토 조건을 기록한다.
- system/data/interface architecture는 선택한 결정을 구현 가능한 구조와 계약으로 표현한다.
- evidence는 결정과 구현을 검증하지만, 과거 측정이 현재 기준을 자동으로 증명하지 않는다.

## Document Guide

| Document | Question |
|---|---|
| [Concern Coverage](architecture/concern-coverage.md) | 어떤 관점을 항상 검사하며 어디에서 다루는가? |
| [Requirements](requirements/rtb-auction-system-requirements.md) | 시스템이 무엇을 하고 어떤 기준을 만족해야 하는가? |
| [ASR](architecture/architecture-significant-requirements.md) | 어떤 요구가 구조를 바꾸며 어떻게 검증하는가? |
| [Drivers](architecture/architecture-drivers.md) | 어떤 힘과 충돌이 설계를 움직이는가? |
| [Decision Register](architecture/decisions/architecture-decision-register.md) | 무엇을 선택했고 검증은 어디까지 되었는가? |
| [System Architecture](architecture/system-architecture.md) | 구성요소, 책임, runtime/resource 구조는 무엇인가? |
| [Data Architecture](architecture/data-architecture.md) | 데이터 ownership, lifetime, consistency 계약은 무엇인가? |
| [Interface Contracts](architecture/interface-contracts.md) | provider와 OpenRTB 경계의 정확한 의미는 무엇인가? |
| [Performance Evidence](evidence/performance/README.md) | 어떤 측정이 현재 acceptance evidence로 유효한가? |

## IDs and Traceability

| Prefix | Meaning |
|---|---|
| `G-*` | Project goal |
| `FR-*` | Functional requirement |
| `QR-*` | Quality requirement |
| `C-*` | Constraint |
| `A-*` | Assumption |
| `OOS-*` | Out of scope |
| `ASR-*` | Architecture-significant requirement |
| `AD-*` | Architecture driver |
| `ADR-*` | Architecture decision record |
| `VP-*` | Verification profile |

- 모든 ASR은 하나 이상의 requirement/constraint를 참조한다.
- 모든 Driver는 관련 ASR과 목표·제약·위험을 참조한다.
- 모든 ADR은 ASR과 Driver를 참조하고 architecture description에 반영한다.
- 구현 또는 실험에서 새로운 요구가 발견되면 ADR에 숨기지 않고 requirements로 되돌린다.

## Maintenance Rules

1. 새 견해를 곧바로 문서 단계로 추가하지 않고 concern coverage에 먼저 배치한다.
2. 요구사항에는 동작과 합격 기준, ADR에는 선택과 trade-off, architecture description에는 결과 구조를 쓴다.
3. ADR acceptance와 verification status를 분리한다.
4. 성능 수치에는 commit, 환경, topology, workload, domain correctness를 함께 기록한다.
5. 범위 밖 항목은 요구사항 변경 전까지 ADR 후보로 만들지 않는다.
6. 같은 규칙을 여러 문서에 복제하지 않고 ID와 링크로 연결한다.
