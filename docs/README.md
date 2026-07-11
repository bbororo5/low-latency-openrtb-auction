# 문서 안내

현재 단계: ASR 초안 완료, 아키텍처 동인 협의 중

```text
docs/
├── README.md
├── requirements/
│   ├── product-domain-requirements.md
│   └── workload-data-verification.md
└── architecture/
    ├── asr.md
    └── architecture-drivers.md
```

| 문서 | 내용 |
|---|---|
| [제품·도메인 요구사항](requirements/product-domain-requirements.md) | 범위, 경매·캠페인·금액 규칙, 기능·품질 요구사항 |
| [부하·데이터·검증 기준](requirements/workload-data-verification.md) | 요청률, 데이터 규모, 장애, 합격 기준 |
| [ASR](architecture/asr.md) | 아키텍처에 중요한 품질 시나리오와 우선순위 |
| [아키텍처 동인](architecture/architecture-drivers.md) | 반복되는 구조적 압력, 데이터 불변식, 책임 경계, 결정 질문 |

```text
요구사항 → ASR·아키텍처 동인 → ADR → 아키텍처 → 구현·검증
```

- 문서에는 합의된 결과만 남긴다.
- 다음 단계의 빈 파일은 미리 만들지 않는다.
- 과거 설계와 측정은 Git 기록에서만 조회한다.
