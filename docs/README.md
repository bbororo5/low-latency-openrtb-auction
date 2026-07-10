# RTB Architecture Documentation Baseline

상태: Active 1.0
목적: 요구사항에서 ASR과 Architecture Driver를 각각 도출하고, 두 산출물을 근거로 ADR을 작성한다.

이 디렉터리는 프로젝트의 정식 문서 기준선이다. `evidence/`의 날짜별 기록은 검증 증거이며 규범 요구사항이 아니다.

## 디렉터리 구조

```text
docs/
├── requirements/
│   └── rtb-auction-system-requirements.md
├── architecture/
│   ├── architecture-significant-requirements.md
│   ├── architecture-drivers.md
│   └── decisions/
│       ├── README.md
│       ├── architecture-decision-backlog.md
│       └── adr-*.md
└── evidence/
    └── performance/
        └── YYYY-MM-DD-*.md
```

## 문서 흐름

```text
RTB Auction System Requirements
├── Architecture Significant Requirements
└── Architecture Drivers
          │
          └── Architecture Decision Records
                     └── Performance Evidence
```

ASR과 Architecture Driver는 서로의 입력이 아니다. 둘 다 요구사항 베이스라인을 직접 참조한다.

| Layer | Document | 답하는 질문 |
|---|---|---|
| Requirements | [`requirements/rtb-auction-system-requirements.md`](requirements/rtb-auction-system-requirements.md) | 시스템은 무엇을 해야 하고 어떤 조건을 만족해야 하는가? |
| ASR | [`architecture/architecture-significant-requirements.md`](architecture/architecture-significant-requirements.md) | 어떤 요구가 아키텍처를 실질적으로 바꾸며 어떻게 검증하는가? |
| Drivers | [`architecture/architecture-drivers.md`](architecture/architecture-drivers.md) | 어떤 목표·제약·위험의 조합이 설계를 움직이는가? |
| Decisions | [`architecture/decisions/architecture-decision-backlog.md`](architecture/decisions/architecture-decision-backlog.md) | 어떤 대안을 어떤 기준으로 비교하고 무엇을 선택했는가? |
| Evidence | [`evidence/performance/`](evidence/performance/) | 결정과 구현을 어떤 환경·부하·결과로 검증했는가? |

## ID와 추적 규칙

| Prefix | Meaning |
|---|---|
| `G-*` | Project goal |
| `FR-*` | Functional requirement |
| `QR-*` | Quality requirement |
| `C-*` | Constraint |
| `A-*` | Assumption |
| `OOS-*` | Out of scope |
| `ASR-*` | Architecture Significant Requirement |
| `AD-*` | Architecture Driver |
| `ADR-*` | Architecture Decision Record |
| `VP-*` | Verification profile |

`FR-003~008` 표기는 `FR-003`부터 `FR-008`까지의 inclusive range를 뜻하며 새로운 ID가 아니다.

- 모든 ASR은 하나 이상의 `FR`, `QR`, `C`를 참조한다.
- 모든 Driver는 요구사항 ID와 관련 ASR ID를 참조한다.
- 모든 ADR은 해결할 `ASR`과 `AD`를 참조한다.
- 요구사항이 바뀌면 관련 ASR, Driver, ADR의 유효성을 다시 확인한다.
- 구현이나 실험에서 새 요구가 발견되면 ADR에 숨기지 않고 요구사항 베이스라인으로 되돌린다.

## 문서 유지 원칙

1. 같은 규칙을 여러 문서에 반복하지 않고 ID로 참조한다.
2. 요구사항에는 `무엇`과 합격 기준을 쓰고, `어떻게`는 ADR에서 결정한다.
3. 수치가 없는 품질 요구는 원칙이 아니라 미결 항목으로 취급한다.
4. 관측된 성능과 목표 성능을 구분한다. 실험 결과가 요구사항을 자동으로 바꾸지 않는다.
5. 범위 밖 항목은 미래 ASR로 승격하지 않는다. 범위를 바꿀 때 먼저 요구사항을 수정한다.
6. ADR은 결정을 내린 시점의 증거를 기록하며, 사후 정당화 문서로 만들지 않는다.

## 변경 절차

1. 범위·동작·품질 목표 변경은 `requirements/rtb-auction-system-requirements.md`에서 시작한다.
2. 변경된 요구가 관련 ASR과 Driver를 무효화하는지 확인한다.
3. 영향을 받은 ADR을 재검토하거나 새 ADR을 Proposed로 추가한다.
4. 구현 후 verification profile을 실행하고 evidence를 남긴다.
