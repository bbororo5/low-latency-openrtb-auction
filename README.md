# Low-Latency OpenRTB Bidding System

OpenRTB 2.6의 입찰 요청(`BidRequest`) / 입찰 응답(`BidResponse`) 흐름을 기반으로, 실시간 광고 입찰 요청을 처리하고 제한 시간 안에 낙찰자와 낙찰가를 결정하는 저지연 RTB 입찰 시스템을 단계적으로 구현하는 프로젝트입니다.

현재 단계는 **초기 구현 준비**입니다. Project Goal, PRD, Architecture, Tech Spec의 큰 방향은 정리되었고, 이제 BidRequest부터 BidResponse 수집과 낙찰 결정까지의 기본 동작을 구현합니다.

## Project Focus

이 프로젝트는 전체 광고 플랫폼을 구현하는 것이 아니라, `게시자 -> 경량 SSP <-> 경량 DSP <- 광고주` 관계에서 광고 판매 측 경매 흐름과 광고 구매 측 입찰 판단 흐름의 성능 핵심 경로(hot path)에 집중합니다.

> BidRequest를 등록된 여러 경량 DSP에게 전달하고, 각 경량 DSP의 입찰 여부와 입찰가 판단을 거쳐, 제한 시간 안에 도착한 유효한 BidResponse 중 낙찰자와 낙찰가를 결정하는 문제.

관심사는 다음과 같습니다.

- OpenRTB 2.6의 핵심 요청/응답 계약 이해
- OpenRTB `Imp.banner` / `Imp.video` / `Imp.native` 기반 광고 타입 표현
- 실시간 광고 입찰 요청 처리
- 광고 슬롯, 광고 타입, bidfloor 등 입찰 판단 정보 해석
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
2. PRD
3. Architecture
4. Tech Spec
5. Feature Development
6. Test & Validation
7. Performance Measurement
8. ADR
9. Optimization
10. Retrospective

ADR은 구현 전에 억지로 작성하지 않습니다. 구현과 성능 측정 중 실제 갈림길이 생겼을 때 선택지, trade-off, 결정 근거를 기록합니다.

## Documents

- [Project Goal](docs/PROJECT_GOAL.md)
- [PRD](docs/PRD.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Tech Spec](docs/TECH_SPEC.md)
