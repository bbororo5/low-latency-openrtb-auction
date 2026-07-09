# Product Requirements: Low-Latency OpenRTB Bidding System

이 문서는 시스템이 풀어야 할 문제와 범위를 정의한다. 기술 선택, 컴포넌트 구조, API 필드, 데이터 정합성, 성능 실험 결과는 전용 문서에서 다룬다.

## 1. Product Goal

Provider Slot Request를 받은 SSP가 광고 슬롯과 inventory를 해석해 OpenRTB BidRequest를 만들고, 여러 DSP의 BidResponse를 제한 시간 안에 수집해 winner 또는 no-winner를 결정한다.

이 프로젝트는 광고 플랫폼 전체가 아니라 RTB 경매 hot path를 다룬다.

## 2. Core Problem

RTB 경매에서는 여러 DSP가 동시에 응답하지만, 모든 DSP가 정상적으로 또는 제시간에 응답하지 않는다.

시스템은 다음을 구분해야 한다.

| Outcome | Meaning |
|---|---|
| `WINNER` | 제한 시간 안에 유효한 bid가 있어 낙찰자가 결정됐다. |
| `NO_WINNER` | 경매는 정상 종료됐지만 유효한 bid가 없다. |
| `NO_BID` | DSP가 정상적으로 입찰하지 않았다. |
| `TIMEOUT` | DSP가 제한 시간 안에 응답하지 않았다. |
| `LATE_BID` | DSP bid가 제한 시간 이후 도착했다. |
| `INVALID_BID` | DSP 응답이 원 요청이나 경매 규칙과 맞지 않는다. |

핵심은 실패를 하나로 묶지 않는 것이다. `NO_BID`, `NO_WINNER`, `TIMEOUT`, `INVALID_BID`는 서로 다른 의미를 가진다.

## 3. Scope

포함한다:

- Provider Slot Request 수신
- inventory lookup
- OpenRTB BidRequest 생성
- 여러 DSP로 BidRequest fan-out
- BidResponse 또는 no-bid 수집
- timeout, late bid, invalid bid 분류
- first-price winner selection
- AuctionResult 반환
- latency와 결과 품질 측정

포함하지 않는다:

- 실제 광고 렌더링
- browser/mobile SDK 또는 ad tag 구현
- impression, click, conversion tracking
- billing, settlement, ledger
- 광고 운영 백오피스
- 실제 외부 SSP/DSP 연동
- DSP routing optimization
- Kubernetes 운영 검증

## 4. Actors

| Actor | Role |
|---|---|
| Publisher / Ad Slot | 광고 슬롯이 열렸다는 요청을 보낸다. |
| Lightweight SSP | provider 요청을 경매 가능한 OpenRTB BidRequest로 만들고 경매를 수행한다. |
| Lightweight DSP | BidRequest를 평가해 bid 또는 no-bid를 반환한다. |
| Advertiser / Campaign System | DSP가 사용할 캠페인 기준 데이터의 원천 역할을 한다. |

`Lightweight`는 운영 제품 전체가 아니라 hot path 학습과 검증을 위한 축소 구현이라는 뜻이다.

## 5. Main Scenario

1. Publisher / Ad Slot이 Provider Slot Request를 보낸다.
2. SSP가 provider, placement, media constraint를 검증한다.
3. SSP가 inventory 기준으로 OpenRTB BidRequest를 만든다.
4. SSP가 등록된 DSP들에게 같은 BidRequest를 보낸다.
5. DSP는 bid 또는 no-bid를 반환한다.
6. SSP는 제한 시간과 유효성 규칙으로 DSP 결과를 분류한다.
7. 유효한 bid가 있으면 first-price 규칙으로 winner를 결정한다.
8. 유효한 bid가 없으면 정상 `NO_WINNER` 결과를 반환한다.

## 6. Requirements

| ID | Requirement |
|---|---|
| FR-001 | SSP는 provider-facing 요청과 OpenRTB BidRequest를 구분해야 한다. |
| FR-002 | SSP는 지원하지 않는 placement, media type, slot constraint를 경매 시작 전에 거절해야 한다. |
| FR-003 | SSP는 하나의 경매 요청에 대해 동일한 BidRequest를 여러 DSP에 전달해야 한다. |
| FR-004 | DSP는 요청과 캠페인 기준 데이터를 비교해 bid 또는 no-bid를 결정해야 한다. |
| FR-005 | SSP는 deadline 이후 도착한 bid를 winner 후보에서 제외해야 한다. |
| FR-006 | SSP는 Bid Judgment를 통과한 candidate만 Winner Decision에 전달해야 한다. |
| FR-007 | SSP는 no-winner를 시스템 장애가 아닌 정상 경매 결과로 반환해야 한다. |
| FR-008 | 시스템은 latency, timeout, late bid, invalid bid, no-bid, no-winner를 측정할 수 있어야 한다. |

## 7. Quality Requirements

| Quality | Requirement |
|---|---|
| Deadline compliance | 경매 결과는 제한 시간 안에 결정되어야 한다. |
| Low latency | p95/p99 latency를 기준으로 성능을 해석한다. |
| Correctness | invalid bid와 late bid는 winner가 될 수 없다. |
| Failure isolation | 일부 DSP 실패가 전체 경매 실패가 되면 안 된다. |
| Observability | 결과 품질과 지연 원인을 구분해 설명할 수 있어야 한다. |

측정 가능한 품질 시나리오는 `architecture-significant-requirements.md`에서 다룬다.

## 8. Assumptions

- SSP-DSP 경계는 OpenRTB subset을 사용한다.
- 기본 범위는 banner와 simple video다.
- 하나의 BidRequest는 하나의 impression만 가진다.
- 통화는 현재 `USD`만 사용한다.
- Campaign/Inventory 원본과 hot path serving copy는 개념적으로 분리한다.
- 현재 money, ledger, event output은 구현 범위 밖이다.

## 9. Milestones

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

## 10. Open Questions

- Bid tie-break rule을 어떤 기준으로 고정할 것인가?
- Empty `seatbid`를 no-bid로 처리할 것인가?
- Serving copy freshness를 어느 정도 허용할 것인가?
- Event output과 idempotency를 언제 설계 범위에 포함할 것인가?
- Money/ledger를 도입할 때 bid 시점 reservation과 win 시점 charging 중 무엇을 택할 것인가?
