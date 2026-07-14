# 문서 안내

현재 단계: 첫 번째 아키텍처 설계 주기 — 분산 예산 예약 결정과 상세화

```text
docs/
├── README.md
├── requirements/
│   ├── product-domain-requirements.md
│   └── workload-data-verification.md
└── architecture/
    ├── asr.md
    ├── architecture-drivers.md
    ├── decisions/
    │   └── ADR-001-distributed-budget-reservation.md
    ├── design-options/
    │   └── budget-reservation.md
    ├── driver-analysis/
    │   ├── data-intensive-systems.md
    │   ├── domain-boundaries.md
    │   ├── evolutionary-architecture.md
    │   ├── operations-and-failure.md
    │   └── trust-boundaries.md
    └── views/
        └── budget-reservation.md
```

| 문서 | 내용 |
|---|---|
| [제품·도메인 요구사항](requirements/product-domain-requirements.md) | 경매·캠페인·페이싱·예산·통지·장애 계약 |
| [부하·데이터·검증 기준](requirements/workload-data-verification.md) | 트래픽 구간, 시험 분리, 장애 입력과 합격 기준 |
| [ASR](architecture/asr.md) | 구조를 압박하는 성능·금액·페이싱·복구·신뢰 시나리오 |
| [아키텍처 동인](architecture/architecture-drivers.md) | 반복되는 압력, 데이터·책임 경계와 ADR 결정 목록 |
| [데이터 중심 분석](architecture/driver-analysis/data-intensive-systems.md) | 데이터 원본성, 수명, 정합성, 복구 압력 |
| [도메인 경계 분석](architecture/driver-analysis/domain-boundaries.md) | 의미, 불변식, 책임 소유권과 경계 간 계약 |
| [진화적 아키텍처 분석](architecture/driver-analysis/evolutionary-architecture.md) | 변화 방향, 유지할 성질과 지속적 판정 기준 |
| [운영·실패 분석](architecture/driver-analysis/operations-and-failure.md) | 장애 전파, 성능 저하, 자원 고갈과 복구 압력 |
| [신뢰 경계 분석](architecture/driver-analysis/trust-boundaries.md) | 사실을 확정할 권한과 경계별 검증 |
| [분산 예산 예약 대안](architecture/design-options/budget-reservation.md) | A~D 모델의 트레이드오프와 소거·선택 근거 |
| [ADR-001 분산 캠페인 예산 예약](architecture/decisions/ADR-001-distributed-budget-reservation.md) | 예산 권한 선할당과 로컬 예약 선택 |
| [캠페인 예산 예약 구조](architecture/views/budget-reservation.md) | 선택한 구조의 권한·예약·과금·장애 흐름 |

```text
요구사항 → ASR → 동인 분석·종합 → 설계 후보 → ADR → 공식 아키텍처 → 구현·검증
```

- 요구사항·ASR·동인·ADR·공식 아키텍처에는 합의된 결과만 남긴다.
- `design-options/`에는 선택 전 후보를 두되 상태를 명시하고 ADR에서 결론을 참조한다.
- 다음 단계의 빈 파일은 미리 만들지 않는다.
- 과거 설계와 측정은 Git 기록에서만 조회한다.
