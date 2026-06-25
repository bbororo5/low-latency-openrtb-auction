# Low-Latency OpenRTB Bidding System

OpenRTB 2.6의 입찰 요청(`BidRequest`) / 입찰 응답(`BidResponse`) 흐름을 기반으로, 실시간 광고 입찰 요청을 처리하고 제한 시간 안에 낙찰자와 낙찰가를 결정하는 저지연 RTB 입찰 시스템을 단계적으로 구현하는 프로젝트입니다.

현재 단계는 **Tech Spec 작성**입니다. 아직 기술 스택, 물리 컴포넌트 분리, 배포 방식, 최적화 구현은 확정하지 않습니다.

## Project Focus

이 프로젝트는 전체 광고 플랫폼을 구현하는 것이 아니라, `게시자 -> SSP/Exchange <-> DSP/Bidder <- 광고주` 관계에서 광고 판매 측 경매 흐름과 광고 구매 측 입찰 판단 흐름의 성능 핵심 경로(hot path)에 집중합니다.

> BidRequest를 등록된 여러 Mock Bidder에게 전달하고, 각 Bidder의 입찰 여부와 입찰가 판단을 거쳐, 제한 시간 안에 도착한 유효한 BidResponse 중 낙찰자와 낙찰가를 결정하는 문제.

관심사는 다음과 같습니다.

- OpenRTB 2.6의 핵심 요청/응답 계약 이해
- 실시간 광고 입찰 요청 처리
- 광고 슬롯, 광고 타입, bidfloor 등 입찰 판단 정보 해석
- 입찰 여부와 입찰가 결정
- 여러 Bidder 응답 수집
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
- Bidder 라우팅 최적화
- 전체 OpenRTB 2.6 스펙 구현
- Kubernetes 기반 운영 검증
- 클라우드/무료 배포 환경에서의 절대 성능 증명

## Planned Workflow

이 프로젝트는 아래 순서로 진행합니다.

1. Project Goal
2. PRD
3. Architecture
4. Tech Spec
5. ADR
6. Feature Development
7. Test & Validation
8. Performance Measurement
9. Optimization
10. Retrospective

각 단계의 산출물을 문서로 남겨, 단순 구현 결과가 아니라 문제 정의, 의사결정, 검증 과정을 보여주는 것을 목표로 합니다.

## Documents

- [Project Goal](docs/PROJECT_GOAL.md)
- [PRD](docs/PRD.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Tech Spec](docs/TECH_SPEC.md)
