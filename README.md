# Low-Latency OpenRTB Bidding System

이 프로젝트는 RTB 광고 도메인을 빌려, 백엔드 성능 엔지니어링에서 중요한 두 가지 문제를 다룹니다.

- **제한 시간 안에서의 응답 지연 관리**: 평균 응답 시간이 아니라 p95/p99 latency와 제한 시간 내 응답률을 기준으로 경매 hot path를 평가합니다. RTB에서는 늦게 도착한 응답이 좋은 가격이어도 사용할 수 없기 때문에, tail latency는 결과 품질과 직접 연결됩니다.
- **고빈도 요청에서의 동시 처리 한계 분석**: 광고 슬롯 요청은 고빈도로 발생하고, 하나의 요청은 여러 DSP 호출로 fan-out됩니다. 이 프로젝트는 RPS를 높일 때 in-flight 작업, thread, connection, HTTP/JSON 처리, DSP 호출 비용이 어떻게 병목을 만드는지 관측합니다.

이 두 문제를 p95/p99 latency, timeout rate, DSP별 응답 분포, HTTP/JSON/경매/fan-out baseline으로 측정하고 개선합니다.

현재 단계는 provider-facing slot request 경로, SSP-DSP OpenRTB 경계, 기본 DSP fan-out, 낙찰 판단, k6 성능 스크립트를 함께 맞추는 단계입니다.

## RTB Domain Overview

RTB(Real-Time Bidding)는 광고 슬롯이 열리는 순간 여러 구매 측 시스템이 입찰에 참여하고, 제한 시간 안에 광고 노출 기회의 구매자를 결정하는 실시간 경매 방식입니다.

이 흐름에서 SSP(Supply-Side Platform)는 publisher의 광고 슬롯 요청을 받아 입찰 가능한 요청으로 만들고, 여러 DSP(Demand-Side Platform)에 전달합니다. DSP는 캠페인 조건과 입찰 전략에 따라 bid 또는 no-bid를 반환하고, SSP는 제한 시간 안에 도착한 유효한 bid 중 winner를 결정합니다.

OpenRTB는 SSP와 DSP 사이에서 입찰 요청(`BidRequest`)과 입찰 응답(`BidResponse`)을 주고받기 위한 표준 표현입니다.

## Project Overview

이 프로젝트는 광고 슬롯이 열렸을 때 SSP가 여러 DSP에 입찰을 요청하고, 제한 시간 안에 도착한 응답만으로 낙찰 여부를 결정하는 경매 실행 경로에 집중합니다.

- SSP는 provider-facing 요청을 검증하고 OpenRTB 2.6 `BidRequest`를 생성합니다.
- 여러 경량 DSP에 같은 `BidRequest`를 전달하고 제한 시간 안에 응답을 수집합니다.
- `no-bid`, `timeout`, `late bid`, `invalid bid`를 구분하고, 유효한 bid 중 winner 또는 no-winner를 결정합니다.

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
