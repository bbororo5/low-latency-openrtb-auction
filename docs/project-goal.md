# Project Goal

## 1. 문제 정의

이 프로젝트는 광고 슬롯이 열렸다는 `Provider Slot Request`가 SSP에 도착한 순간부터, SSP가 제한 시간 안에 `AuctionResult`를 반환하는 순간까지의 RTB hot path를 다룬다.

핵심 문제는 다음과 같다.

> SSP가 provider-facing 요청을 검증하고 inventory 기준으로 DSP-facing OpenRTB `BidRequest`를 만든 뒤, 여러 경량 DSP의 응답을 제한 시간 안에 수집하고, timeout, late bid, invalid bid, no-bid를 구분해 유효한 bid 중 winner 또는 no-winner를 결정하는 것.

이 프로젝트는 광고 도메인 전체를 구현하기 위한 것이 아니다. 운영 수준의 SSP와 DSP 전체를 만들지 않고, `게시자 -> 경량 SSP <-> 경량 DSP <- 광고주` 관계에서 광고 판매 측 경매 흐름과 광고 구매 측 입찰 판단 흐름의 핵심 실행 경로를 작게 구현해 검증한다.

## 2. 문제를 풀었다는 기준

이 단계의 성공은 운영 제품 완성이 아니라, 제한 시간과 부분 실패가 있는 RTB 경매 흐름을 검증 가능한 형태로 구현하는 것이다.

프로젝트는 최소한 다음을 보여줘야 한다.

- `Provider Slot Request`부터 `AuctionResult`까지의 경계가 코드와 문서에서 일관된다.
- deadline, partial failure, invalid response가 있는 상황에서도 경매 결과를 분류한다.
- winner는 valid bid 후보에서만 나오며, 후보가 없으면 정상 `NO_WINNER`가 된다.
- 이 판단을 테스트, latency 측정, 결과 메트릭으로 검증할 수 있다.

## 3. 현재 범위

현재 목표 설정 단계에서는 아래 범위까지만 확정한다.

- OpenRTB 2.6의 입찰 요청(`BidRequest`) / 입찰 응답(`BidResponse`) 흐름을 기반으로 한다.
- SSP와 DSP 사이의 경계 객체는 OpenRTB 표현 방식을 우선한다. 기본 광고 범위는 banner와 simple video다.
- 시스템의 관심사는 입찰 요청 처리, 입찰 여부와 입찰가 판단, BidResponse 생성/수집, 낙찰자/낙찰가 결정까지이다.
- 실제 광고 노출, 클릭, 과금, 리포팅은 제외한다.
- 상세 컴포넌트 구조와 기술 선택은 Implementation Technical Specification 단계에서 결정한다.
- 최적화 방식은 기준 구현과 측정 이후 결정한다.

## 4. 기술적 초점

이 프로젝트는 다음 문제 축에 집중한다.

- OpenRTB 입찰 요청/응답 계약을 기반으로 한 요청 처리
- 제한 시간 안에서의 입찰 응답 수집과 실패 케이스 분류
- 유효한 입찰만을 대상으로 한 낙찰자/낙찰가 결정
- 측정 가능한 성능 지표를 기반으로 한 병목 분석과 개선

## 5. 진행 방식

프로젝트는 아래 순서로 진행한다.

1. Project Goal
2. Product Requirements
3. Data Architecture
4. Architecture Significant Requirements
5. Architecture Description
6. API / Interface Specification
7. Implementation Technical Specification
8. Architecture Decision Records
9. Feature Development
10. Test & Validation
11. Performance Measurement
12. Optimization
13. Retrospective

각 단계에서는 현재 단계에 필요한 결정만 내린다. 이후 단계의 결정을 앞당겨 확정하지 않는다.

## 6. 산출물 원칙

이 프로젝트는 단순히 "무엇을 구현했다"를 보여주는 데 그치지 않는다. 각 산출물은 다음 질문에 답해야 한다.

- 어떤 문제를 풀려고 했는가?
- 어떤 제약 조건이 있었는가?
- 어떤 선택지를 고려했는가?
- 왜 그 선택을 했는가?
- 어떻게 검증했는가?
- 결과적으로 무엇을 배웠는가?
