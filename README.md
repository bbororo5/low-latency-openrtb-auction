# Low-Latency OpenRTB Bidding System

이 프로젝트는 RTB 광고 도메인을 빌려, 백엔드 성능 엔지니어링에서 중요한 두 가지 문제를 다룹니다.

- **제한 시간 안에서의 응답 지연 관리**: 평균 응답 시간이 아니라 p95/p99 latency와 제한 시간 내 응답률을 기준으로 경매 hot path를 평가합니다. RTB에서는 늦게 도착한 응답이 좋은 가격이어도 사용할 수 없기 때문에, tail latency는 결과 품질과 직접 연결됩니다.
- **고빈도 요청에서의 동시 처리 한계 분석**: 광고 슬롯 요청은 고빈도로 발생하고, 하나의 요청은 여러 DSP 호출로 fan-out됩니다. 이 프로젝트는 RPS를 높일 때 in-flight 작업, thread, connection, HTTP/JSON 처리, DSP 호출 비용이 어떻게 병목을 만드는지 관측합니다.

이 두 문제를 p95/p99 latency, timeout rate, DSP별 응답 분포, HTTP/JSON/경매/fan-out baseline으로 측정하고 개선합니다.

현재 baseline은 provider-facing slot request, SSP-DSP OpenRTB 경계, bounded DSP fan-out, deterministic winner, immutable startup snapshot, E2E와 k6 verification profile을 갖춥니다. Reference 환경의 최신 성능 acceptance evidence는 아직 수집 전입니다.

## RTB Domain Overview

RTB(Real-Time Bidding)는 광고 슬롯이 열리는 순간 여러 구매 측 시스템이 입찰에 참여하고, 제한 시간 안에 광고 노출 기회의 구매자를 결정하는 실시간 경매 방식입니다.

이 흐름에서 SSP(Supply-Side Platform)는 publisher의 광고 슬롯 요청을 받아 입찰 가능한 요청으로 만들고, 여러 DSP(Demand-Side Platform)에 전달합니다. DSP는 캠페인 조건과 입찰 전략에 따라 bid 또는 no-bid를 반환하고, SSP는 제한 시간 안에 도착한 유효한 bid 중 winner를 결정합니다.

OpenRTB는 SSP와 DSP 사이에서 입찰 요청(`BidRequest`)과 입찰 응답(`BidResponse`)을 주고받기 위한 표준 표현입니다.

## Project Overview

이 프로젝트는 광고 슬롯이 열렸을 때 SSP가 여러 DSP에 입찰을 요청하고, 제한 시간 안에 도착한 응답만으로 낙찰 여부를 결정하는 경매 실행 경로에 집중합니다.

- SSP는 provider-facing 요청을 검증하고 OpenRTB 2.6 `BidRequest`를 생성합니다.
- 여러 경량 DSP에 같은 `BidRequest`를 전달하고 제한 시간 안에 응답을 수집합니다.
- `no-bid`, `timeout`, `invalid bid`, `error`를 구분하고, cutoff 이후 응답은 별도 진단으로 다루며, 유효한 bid 중 winner 또는 no-winner를 결정합니다.

## Architecture Workflow

문서는 다음 산출물 흐름을 따릅니다.

```text
Concern Coverage
      ↓
Development Requirements
      ↓
Architecture Significant Requirements
      ↓
Architecture Drivers
      ↓
ADR ↔ System/Data/Interface Architecture
      ↓
Implementation ↔ Verification Evidence
```

ASR은 검증 가능한 아키텍처 중요 요구이고, Architecture Driver는 ASR·목표·제약·위험을 설계 압력으로 종합합니다. ADR의 선택 상태와 verification 상태를 분리해, 결정은 고정하면서도 아직 없는 성능 증거를 완료된 것처럼 취급하지 않습니다.

## Documents

- [Documentation Baseline](docs/README.md)
- [RTB Auction System Requirements](docs/requirements/rtb-auction-system-requirements.md)
- [Architecture Significant Requirements](docs/architecture/architecture-significant-requirements.md)
- [Architecture Drivers](docs/architecture/architecture-drivers.md)
- [Architecture Concern Coverage](docs/architecture/concern-coverage.md)
- [System Architecture](docs/architecture/system-architecture.md)
- [Data Architecture](docs/architecture/data-architecture.md)
- [Provider and OpenRTB Interface Contracts](docs/architecture/interface-contracts.md)
- [Architecture Decision Register](docs/architecture/decisions/architecture-decision-register.md)
- [Architecture Decision Records](docs/architecture/decisions)
- [Performance Evidence](docs/evidence/performance/README.md)
