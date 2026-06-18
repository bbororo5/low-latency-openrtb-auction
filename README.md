# Low-Latency OpenRTB Auction Project

OpenRTB 2.6의 `BidRequest` / `BidResponse` 흐름을 소재로, 제한 시간 안에서 여러 Bidder의 응답을 수집하고 낙찰자를 결정하는 백엔드 시스템을 단계적으로 구현하는 프로젝트입니다.

현재 단계는 **프로젝트 목표 설정**입니다. 아직 상세 설계, 기술 선택, 모듈 구조, 최적화 방식은 확정하지 않습니다.

## Project Focus

이 프로젝트는 전체 광고 시스템을 구현하는 것이 아니라, 다음 문제에 집중합니다.

> 하나의 BidRequest를 여러 Bidder가 처리하고, 제한 시간 안에 도착한 유효한 BidResponse 중 낙찰자를 결정하는 문제.

관심사는 다음과 같습니다.

- OpenRTB 2.6의 핵심 요청/응답 계약 이해
- read-heavy 성격의 Bidder 처리 흐름
- 여러 Bidder에 대한 동시 요청 처리
- timeout, no-bid, invalid bid 처리
- 낙찰자 결정 로직
- 성능 측정과 개선 과정

## Out of Scope

현재 프로젝트 범위에서는 아래 기능을 다루지 않습니다.

- 실제 광고 렌더링
- impression tracking
- click tracking
- billing
- 리포팅
- 전체 OpenRTB 2.6 스펙 구현

## Planned Workflow

이 프로젝트는 아래 순서로 진행합니다.

1. Project Goal
2. PRD
3. Tech Spec
4. ADR
5. Feature Development
6. Test & Validation
7. Performance Measurement
8. Optimization
9. Retrospective

각 단계의 산출물을 문서로 남겨, 단순 구현 결과가 아니라 문제 정의, 의사결정, 검증 과정을 보여주는 것을 목표로 합니다.

## Documents

- [Project Goal](docs/PROJECT_GOAL.md)
