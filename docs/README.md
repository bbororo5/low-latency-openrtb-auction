# 문서 안내

문서는 작성 순서가 아니라 독자의 질문에 따라 탐색한다.

| 알고 싶은 것 | 문서 |
|---|---|
| 무엇을 만드는가 | [제품·도메인 요구사항](requirements/product.md) |
| 어느 부하와 품질을 만족해야 하는가 | [아키텍처 중요 요구사항](requirements/quality.md), [부하·데이터·검증 기준](requirements/workload.md) |
| 무엇이 아키텍처를 압박했는가 | [아키텍처 동인](architecture/drivers.md) |
| 왜 주요 구조를 선택했는가 | [아키텍처 결정](architecture/decisions/) |
| 어떤 관점으로 위험을 검토했는가 | [설계 근거](architecture/rationale/) |

```text
docs/
├── README.md
├── requirements/
│   ├── product.md
│   ├── quality.md
│   └── workload.md
└── architecture/
    ├── drivers.md
    ├── decisions/
    │   └── ADR-001~006
    └── rationale/
        ├── data.md
        ├── domain.md
        ├── evolution.md
        ├── failure.md
        └── trust.md
```

`requirements/`는 시스템이 만족할 계약, `decisions/`는 되돌리기 어려운 선택, `rationale/`는 동인을 발견한 근거를 소유한다. 작성 과정과 과거 구조는 Git 기록에서 확인한다.
