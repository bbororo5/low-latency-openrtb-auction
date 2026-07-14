# 문서 안내

현재 단계: ASR과 아키텍처 동인 확정, ADR 준비

```text
docs/
├── README.md
├── requirements/
│   ├── product-domain-requirements.md
│   └── workload-data-verification.md
└── architecture/
    ├── asr.md
    ├── architecture-drivers.md
    └── reviews/
        ├── data-intensive-systems-review.md
        ├── domain-boundaries-review.md
        ├── evolutionary-architecture-review.md
        ├── operations-failure-review.md
        └── trust-boundaries-review.md
```

| 문서 | 내용 |
|---|---|
| [제품·도메인 요구사항](requirements/product-domain-requirements.md) | 경매·캠페인·페이싱·예산·통지·장애 계약 |
| [부하·데이터·검증 기준](requirements/workload-data-verification.md) | 트래픽 구간, 시험 분리, 장애 입력과 합격 기준 |
| [ASR](architecture/asr.md) | 구조를 압박하는 성능·금액·페이싱·복구·신뢰 시나리오 |
| [아키텍처 동인](architecture/architecture-drivers.md) | 반복되는 압력, 데이터·책임 경계와 ADR 결정 목록 |
| [데이터 중심 검토](architecture/reviews/data-intensive-systems-review.md) | 데이터 원본성, 수명, 정합성, 복구 기준 검토 |
| [도메인 경계 검토](architecture/reviews/domain-boundaries-review.md) | 의미, 불변식, 책임 소유권과 경계 간 계약 검토 |
| [진화적 아키텍처 검토](architecture/reviews/evolutionary-architecture-review.md) | 변화 방향, 유지할 성질, 지속적 판정 기준 검토 |
| [운영·실패 검토](architecture/reviews/operations-failure-review.md) | 장애 전파, 성능 저하, 자원 고갈, 복구 판정 검토 |
| [신뢰 경계 검토](architecture/reviews/trust-boundaries-review.md) | 사실을 확정할 권한, 경계별 검증, 재전송 위험 검토 |

```text
요구사항 → ASR·아키텍처 동인 → ADR → 아키텍처 → 구현·검증
```

- 문서에는 합의된 결과만 남긴다.
- 다음 단계의 빈 파일은 미리 만들지 않는다.
- 과거 설계와 측정은 Git 기록에서만 조회한다.
