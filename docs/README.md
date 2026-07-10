# Documentation

상태: Requirements Baseline complete

현재 단계는 `Requirements` 완료 직후이며 ASR workshop 전이다. 실제 내용이 없는 placeholder, 과거 architecture 결정, 이전 prototype 측정 문서는 현재 tree에 두지 않는다.

## Current Structure

```text
docs/
├── README.md
└── requirements/
    ├── product-and-domain-requirements.md
    └── workload-data-and-verification-profile.md
```

## Current Deliverables

| Document | Purpose |
|---|---|
| [Product and Domain Requirements](requirements/product-and-domain-requirements.md) | 제품 경계, actor, auction/campaign/money 의미, 기능·품질 요구, 제약과 범위 밖 항목 |
| [Workload, Data and Verification Profile](requirements/workload-data-and-verification-profile.md) | 10M/day traffic model, dataset 규모와 skew, deadline/latency/availability 목표, 검증 scenario |

두 문서가 하나의 Requirements package다. 첫 문서는 `무엇을 보장할지`, 두 번째 문서는 `어떤 규모·환경·장애에서 검증할지`를 정의한다.

## Artifact Lifecycle

```text
Requirements package                       ← current
        ↓
ASR + Architecture Drivers                 ← create after workshop
        ↓
ADR alternatives and trade-offs            ← create after Drivers
        ↓
System/Data/Interface Architecture          ← create after decisions
        ↓
Implementation and Verification Evidence   ← create after implementation
```

아직 내용이 없는 미래 산출물은 파일이나 디렉터리로 미리 만들지 않는다.

## Creation Rules

1. ASR은 Requirements에서 사용자와 함께 선별한 뒤 생성한다.
2. Architecture Driver는 합의된 ASR과 quality conflict가 있을 때만 생성한다.
3. ADR은 명확한 decision question과 현실적인 대안이 있을 때만 생성한다.
4. System/Data/Interface Architecture는 ADR 선택 결과를 설명할 때 생성한다.
5. Evidence는 선택된 architecture가 구현된 뒤 현재 workload profile을 실행해 생성한다.
6. 과거 prototype과 측정 기록이 필요하면 Git history에서 조회하며 active docs에 복제하지 않는다.
