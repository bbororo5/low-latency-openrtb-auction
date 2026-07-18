# 문서 안내

현재 단계: 첫 번째 아키텍처 설계 주기 — ADR-002 리전별 독립 원장 결정 완료

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
    │   ├── ADR-001-distributed-budget-reservation.md
    │   └── ADR-002-multi-region-ledger-topology.md
    └── driver-analysis/
        ├── data-intensive-systems.md
        ├── domain-boundaries.md
        ├── evolutionary-architecture.md
        ├── operations-and-failure.md
        └── trust-boundaries.md
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
| [ADR-001 분산 캠페인 예산 예약](architecture/decisions/ADR-001-distributed-budget-reservation.md) | 두 리전의 계층형 에스크로 예산 선택과 트레이드오프 |
| [ADR-002 다중 리전 원장 구조](architecture/decisions/ADR-002-multi-region-ledger-topology.md) | 강한 책임 이전과 리전별 독립 원장 선택 |

```text
요구사항 → ASR → 동인 분석·종합 → ADR의 대안 비교·결정 → 공식 아키텍처 → 구현·검증
```

- 요구사항·ASR·동인·공식 아키텍처에는 합의된 결과만 남기고, 검토 중인 ADR은 상태와 미결 사항을 명시한다.
- ADR에는 고려한 대안, 공통 기준의 트레이드오프, 선택과 재검토 조건을 함께 남긴다.
- 다음 단계의 빈 파일은 미리 만들지 않는다.
- 과거 설계와 측정은 Git 기록에서만 조회한다.
