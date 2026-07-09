# Low-Latency OpenRTB Bidding System

이 프로젝트의 출발점은 광고 도메인을 넓게 구현하는 것이 아니라, **계약된 요청을 검증하고 제한 시간 안에서 외부 참여자의 지연, 무응답, 규약 위반 가능성을 처리하는 백엔드 시스템을 어떻게 설계하고 검증할 것인가**입니다.

채용자가 이 저장소에서 확인할 수 있어야 하는 것은 특정 광고 지식의 양이 아니라, 다음과 같은 엔지니어링 역량입니다.

- 모호한 비즈니스 요청을 실행 가능한 문제로 축소하고 성공 기준을 먼저 세우는 능력
- 내부 명령, 외부 API 계약, 관측 데이터, 영속 상태를 구분해 모델링하는 능력
- deadline이 단순 성능 목표가 아니라 correctness 조건이 되는 흐름을 설계하는 능력
- partial failure, timeout, late response, invalid response를 정상적인 시스템 사건으로 분류하는 능력
- concurrent fan-out/fan-in 경로에서 결과 수집과 결정 책임을 분리하는 능력
- 테스트, latency 측정, 결과 메트릭으로 설계 주장을 검증하는 능력

RTB를 선택한 이유는 이 역량들을 작은 실행 경로 안에서 동시에 드러낼 수 있기 때문입니다. 하나의 광고 슬롯 요청은 provider-facing 요청 검증, DSP-facing OpenRTB 계약 생성, 여러 외부 참여자 호출, 제한 시간 내 응답 수집, 유효 후보 판단, winner/no-winner 결정, latency 측정을 모두 요구합니다.

따라서 이 프로젝트는 Provider Slot Request를 받아 SSP가 OpenRTB 2.6 입찰 요청(`BidRequest`)을 생성하고, 여러 DSP의 입찰 응답(`BidResponse`)을 제한 시간 안에 수집해 낙찰자와 낙찰가를 결정하는 저지연 RTB 입찰 시스템을 단계적으로 구현합니다.

현재 단계는 provider-facing slot request 경로, SSP-DSP OpenRTB 경계, 기본 DSP fan-out, 낙찰 판단, k6 성능 스크립트를 함께 맞추는 단계입니다.

## RTB Problem Scope

이 프로젝트는 전체 광고 플랫폼을 구현하는 것이 아니라, `게시자 -> 경량 SSP <-> 경량 DSP <- 광고주` 관계에서 광고 판매 측 경매 흐름과 광고 구매 측 입찰 판단 흐름의 성능 핵심 경로(hot path)에 집중합니다.

> Provider Slot Request를 SSP가 검증하고 OpenRTB BidRequest로 변환한 뒤, 등록된 여러 경량 DSP에게 전달하고, 제한 시간 안에 도착한 유효한 BidResponse 중 낙찰자와 낙찰가를 결정하는 문제.

관심사는 다음과 같습니다.

- provider-facing slot request와 SSP-DSP OpenRTB 경계 분리
- OpenRTB 2.6의 핵심 요청/응답 계약 이해
- OpenRTB `Imp.banner` / `Imp.video` 기반 광고 타입 표현
- 실시간 광고 입찰 요청 처리
- 광고 슬롯, inventory, 광고 타입, bidfloor 등 입찰 판단 정보 해석
- 입찰 여부와 입찰가 결정
- 여러 경량 DSP 응답 수집
- timeout, invalid response, late bid 처리
- 낙찰자와 낙찰가 결정
- p95/p99 latency와 deadline 내 응답률 측정
- 측정 결과에 기반한 성능 분석 및 최적화

## Out of Scope

현재 프로젝트 범위에서는 아래 기능을 다루지 않습니다.

- 실제 광고 렌더링
- impression tracking
- click tracking
- conversion tracking
- billing
- reporting
- 광고 운영 백오피스
- 실제 외부 DSP/SSP 연동
- DSP 라우팅 최적화
- 전체 OpenRTB 2.6 스펙 구현
- Kubernetes 기반 운영 검증
- 클라우드/무료 배포 환경에서의 절대 성능 증명

## Planned Workflow

이 프로젝트는 아래 순서로 진행합니다.

1. Project Goal
2. Product Requirements
3. Data Architecture
4. Architecture Significant Requirements
5. Architecture Description
6. API / Interface Specification
7. Implementation Technical Specification
8. Feature Development
9. Test & Validation
10. Performance Measurement
11. Architecture Decision Records
12. Optimization
13. Retrospective

ADR은 구현 전에 억지로 작성하지 않습니다. 구현과 성능 측정 중 실제 갈림길이 생겼을 때 선택지, trade-off, 결정 근거를 기록합니다.

## Documents

- [Project Goal](docs/project-goal.md)
- [Documentation Map](docs/README.md)
- [Product Requirements](docs/product-requirements.md)
- [Data Architecture](docs/data-architecture.md)
- [Architecture Significant Requirements](docs/architecture-significant-requirements.md)
- [Architecture Description](docs/architecture-description.md)
- [API / Interface Specification](docs/api-interface-specification.md)
- [Implementation Technical Specification](docs/implementation-technical-specification.md)
- [Observability](docs/observability.md)
- [SSP Message Contract Research](docs/ssp-message-contract-research.md)
- [Performance Baseline](docs/performance-baseline.md)
- [Architecture Decision Records](docs/decisions)
- [Performance Reports](docs/performance-reports)
