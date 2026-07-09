# Product Requirements: Low-Latency OpenRTB Bidding System

이 문서는 시스템이 사용자 관점에서 어떤 문제를 풀어야 하는지 정의한다. 구현 구조, 데이터 아키텍처, API 필드, 성능 실험 결과는 별도 문서에서 다룬다.

## 1. Product Problem

광고 슬롯이 열렸을 때 SSP는 provider-facing 요청을 받아 경매 가능한 요청으로 정규화하고, 여러 DSP의 응답을 제한 시간 안에 수집해 winner 또는 no-winner를 결정해야 한다.

핵심 문제는 모든 DSP가 입찰하거나 제시간에 응답한다는 보장이 없다는 점이다. 시스템은 단순히 성공/실패를 반환하는 것이 아니라, `no-bid`, `timeout`, `late bid`, `invalid bid`, `no-winner`를 구분해야 한다.

## 2. Actors

| Actor | Role |
|---|---|
| Publisher / Ad Slot | 광고 슬롯이 열렸다는 요청을 보낸다. |
| Lightweight SSP | provider 요청을 경매 가능한 OpenRTB `BidRequest`로 만들고 경매를 수행한다. |
| Lightweight DSP | `BidRequest`를 평가해 bid 또는 no-bid를 반환한다. |
| Advertiser / Campaign System | DSP가 입찰 판단에 사용할 캠페인 기준 데이터를 제공한다. |

`Lightweight`는 운영 제품 전체가 아니라 경매 hot path 학습과 검증을 위한 축소 구현이라는 뜻이다.

## 3. Main Use Case

1. Publisher / Ad Slot이 Provider Slot Request를 보낸다.
2. SSP가 provider, placement, media constraint를 검증한다.
3. SSP가 inventory 기준으로 OpenRTB `BidRequest`를 만든다.
4. SSP가 여러 DSP에게 같은 `BidRequest`를 보낸다.
5. DSP는 bid 또는 no-bid를 반환한다.
6. SSP는 deadline과 유효성 규칙으로 DSP 결과를 분류한다.
7. 유효한 bid가 있으면 winner를 결정한다.
8. 유효한 bid가 없으면 정상 no-winner 결과를 반환한다.

## 4. Expected Outcomes

| Outcome | Meaning |
|---|---|
| `WINNER` | 제한 시간 안에 유효한 bid가 있어 winner가 결정됐다. |
| `NO_WINNER` | 경매는 정상 종료됐지만 유효한 bid가 없다. |
| `NO_BID` | DSP가 정상적으로 입찰하지 않았다. |
| `TIMEOUT` | DSP가 제한 시간 안에 응답하지 않았다. |
| `LATE_BID` | DSP bid가 제한 시간 이후 도착했다. |
| `INVALID_BID` | DSP 응답이 원 요청이나 경매 규칙과 맞지 않는다. |

`NO_BID`, `TIMEOUT`, `LATE_BID`, `INVALID_BID`는 winner 후보가 아니다. `NO_WINNER`는 시스템 장애가 아니라 정상 경매 결과다.

## 5. Functional Requirements

| ID | Requirement |
|---|---|
| FR-001 | SSP는 provider-facing 요청과 DSP-facing OpenRTB `BidRequest`를 구분해야 한다. |
| FR-002 | SSP는 provider, placement, media constraint를 검증해야 한다. |
| FR-003 | SSP는 지원하지 않는 placement, media type, slot constraint를 DSP 호출 전에 거절해야 한다. |
| FR-004 | SSP는 유효한 Provider Slot Request를 하나의 경매 실행 단위로 정규화해야 한다. |
| FR-005 | SSP는 하나의 경매 요청에 대해 동일한 `BidRequest`를 여러 DSP에 전달해야 한다. |
| FR-006 | DSP는 `BidRequest`와 캠페인 기준 데이터를 비교해 bid 또는 no-bid를 결정해야 한다. |
| FR-007 | SSP는 no-bid, timeout, late bid, invalid bid를 구분해야 한다. |
| FR-008 | SSP는 deadline 이후 도착한 bid를 winner 후보에서 제외해야 한다. |
| FR-009 | SSP는 Bid Judgment를 통과한 valid bid만 Winner Decision에 전달해야 한다. |
| FR-010 | SSP는 valid bid가 없을 때 정상 no-winner 결과를 반환해야 한다. |
| FR-011 | 시스템은 latency, timeout, late bid, invalid bid, no-bid, no-winner를 측정할 수 있어야 한다. |

## 6. Non-Functional Requirements

| ID | Requirement | Meaning |
|---|---|---|
| NFR-001 | Deadline compliance | 경매 결과는 제한 시간 안에 결정되어야 한다. |
| NFR-002 | Tail latency awareness | 평균 응답 시간이 아니라 p95/p99 latency를 기준으로 성능을 해석해야 한다. |
| NFR-003 | Throughput under concurrency | 고빈도 요청과 DSP fan-out 상황에서 처리량과 포화 지점을 관찰할 수 있어야 한다. |
| NFR-004 | Result correctness | invalid bid, late bid, no-bid, timeout은 winner 후보가 될 수 없다. |
| NFR-005 | Result explainability | winner 또는 no-winner가 왜 나왔는지 DSP 결과 분포로 설명할 수 있어야 한다. |
| NFR-006 | Observability | latency, timeout rate, DSP별 응답 분포, result distribution을 측정할 수 있어야 한다. |

구체적인 품질 시나리오와 측정 기준은 `architecture-significant-requirements.md`에서 다룬다.
