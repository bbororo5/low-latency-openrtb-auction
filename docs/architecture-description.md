# Architecture Description

이 문서는 시스템 경계, 주요 실행 단위, runtime flow를 설명한다. 데이터 상태와 source of truth 경계는 `data-architecture.md`를 선행 기준으로 삼고, 품질속성은 `architecture-significant-requirements.md`, API 계약은 `api-interface-specification.md`, 구현 세부는 `implementation-technical-specification.md`에서 다룬다.

## 1. Context

이 프로젝트는 운영 수준의 광고 플랫폼 전체가 아니라 RTB 경매 hot path를 축소 구현한다.

핵심 관계:

```text
Publisher / Ad Slot
 -> Lightweight SSP
 -> Lightweight DSPs
 -> Lightweight SSP
 -> Publisher / Ad Slot
```

SSP는 공급 지면을 해석하고 경매를 수행한다. DSP는 광고주 캠페인을 기준으로 bid 또는 no-bid를 결정한다.

## 2. System Boundary

포함:

- Provider Slot Request 수신
- inventory serving copy 조회
- OpenRTB BidRequest 생성
- DSP fan-out
- BidResponse/no-bid 수집
- timeout, late bid, invalid bid 분류
- winner/no-winner 결정

제외:

- 실제 광고 렌더링
- tracking
- billing/settlement/ledger
- reporting
- external SSP/DSP integration
- routing optimization
- Kubernetes operation

## 3. C1 System Context

![C1 System Context](../assets/diagrams/c1-system-context.svg)

| External role | Meaning |
|---|---|
| Publisher / Ad Slot | 광고 슬롯 요청을 발생시키는 공급 측 역할 |
| Advertiser / Campaign System | DSP가 사용할 캠페인 기준 데이터의 원천 역할 |
| RTB Bidding System | 경량 SSP와 경량 DSP로 hot path를 검증하는 시스템 |

테스트 데이터 준비 도구나 부하 발생기는 시스템 actor로 표현하지 않는다. 필요할 때 Publisher / Ad Slot 역할을 대체한다.

## 4. C2 Container View

![C2 Container View](../assets/diagrams/c2-container-view.svg)

| Container | Responsibility |
|---|---|
| Lightweight SSP Application | provider 요청을 검증하고 OpenRTB BidRequest를 만들어 DSP에 전달한 뒤 winner/no-winner를 결정한다. |
| Lightweight DSP Application | BidRequest를 평가해 BidResponse 또는 no-bid를 반환한다. |
| Inventory Data Store | provider/placement 원본 데이터의 source of truth 후보 |
| Campaign Data Store | campaign 원본 데이터의 source of truth 후보 |

SSP와 DSP는 서로 다른 store instance를 가질 수 있다. 이는 제품 종류를 다르게 쓴다는 뜻이 아니라 ownership과 장애 경계를 분리한다는 뜻이다.

## 5. Runtime Flow

1. Publisher / Ad Slot이 Provider Slot Request를 보낸다.
2. SSP가 요청과 inventory를 검증한다.
3. SSP가 OpenRTB BidRequest와 내부 AuctionCommand를 만든다.
4. SSP가 등록된 DSP들에게 BidRequest를 보낸다.
5. DSP가 bid 또는 no-bid를 반환한다.
6. SSP가 DSP 결과를 timeout, late bid, invalid bid, no-bid, valid candidate로 분류한다.
7. valid candidate가 있으면 Winner Selector가 winner를 고른다.
8. valid candidate가 없으면 no-winner를 반환한다.
9. SSP가 AuctionResult와 관측 지표를 남긴다.

## 6. Architectural Rules

| Rule | Rationale |
|---|---|
| Provider-facing input과 OpenRTB BidRequest를 분리한다. | 실제 SSP는 광고 슬롯 입력을 해석한 뒤 DSP-facing request를 만든다. |
| Hot path는 source store를 매 요청 동기 조회하지 않는다. | latency와 장애 전파를 줄인다. |
| Inventory/Campaign source of truth와 serving copy를 분리한다. | 재시작, freshness, DB 선택을 독립적으로 설계할 수 있다. |
| DSP result observation과 winner candidate를 분리한다. | timeout/no-bid/error/invalid bid가 winner selection에 섞이지 않는다. |
| Observability와 business event/ledger를 분리한다. | metrics/logs는 비즈니스 진실원이 아니다. |

## 7. Failure Boundaries

| Failure surface | Architectural response |
|---|---|
| Invalid provider request | DSP 호출 없이 거절한다. |
| Unknown or disabled placement | 경매를 시작하지 않는다. |
| DSP no-bid | 정상 비입찰로 기록하고 후보에서 제외한다. |
| DSP timeout | 해당 DSP 결과만 timeout으로 분류한다. |
| Late bid | 가격과 무관하게 후보에서 제외한다. |
| Invalid bid | Bid Judgment에서 제외한다. |
| No valid candidate | 정상 no-winner 결과를 반환한다. |
| Serving copy missing | 추측하지 않고 요청 또는 해당 경로를 중단한다. |

## 8. Measurement Boundary

측정 기준은 `architecture-significant-requirements.md`, metric 전략은 `observability.md`, 실험 결과는 `performance-baseline.md`와 `performance-reports/*`에서 다룬다.

Architecture 관점에서 중요한 것은 세 가지다.

- p95/p99와 deadline compliance를 우선한다.
- timeout, late bid, invalid bid, no-bid, no-winner를 분리한다.
- 성능 테스트 도구는 시스템 컨테이너가 아니라 외부 actor 역할을 한다.

## 9. Deferred Architecture Decisions

- inventory/campaign source store product
- serving copy implementation
- event output container
- budget reservation / ledger architecture
- DSP routing optimization
- Kubernetes deployment architecture
