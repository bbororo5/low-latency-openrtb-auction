# Implementation Technical Specification

이 문서는 API, 데이터, 아키텍처 기준을 코드 구조와 테스트 기준으로 옮긴다. 필드 목록은 `api-interface-specification.md`, source of truth와 정합성은 `data-architecture.md`, 품질속성은 `architecture-significant-requirements.md`를 기준으로 한다.

## 1. Implementation Boundary

구현 범위는 Provider Slot Request부터 AuctionResult까지의 hot path다.

| Area | Included |
|---|---|
| SSP | request validation, inventory lookup, BidRequest creation, DSP fan-out, bid judgment, winner decision |
| DSP | BidRequest interpretation, campaign lookup, matching, pricing, BidResponse/no-bid creation |
| Shared | OpenRTB subset model, media type normalization, result classification |

범위 밖: 광고 렌더링, billing, settlement, reporting, external SSP/DSP integration, Kubernetes operation.

## 2. Runtime Flow

```text
Provider Slot Request
 -> Slot Request Handler
 -> Inventory Catalog
 -> BidRequest Factory
 -> Auction Flow
 -> DSP Gateway
 -> Bid Judge
 -> Winner Selector
 -> AuctionResult
```

핵심 구현 규칙:

- Provider-facing input은 OpenRTB 객체가 아니다.
- OpenRTB BidRequest는 SSP가 생성한다.
- DSP 호출 결과(`DspCallResult`)는 아직 winner candidate가 아니다.
- Bid Judge를 통과한 candidate만 Winner Selector로 전달한다.
- `NO_WINNER`는 정상 결과다.

## 3. SSP Components

| Component | Responsibility |
|---|---|
| Slot Request Handler | Provider Slot Request를 검증하고 경매 시작 가능 여부를 결정한다. |
| Inventory Catalog | provider/placement 기준의 serving copy를 제공한다. |
| BidRequest Factory | inventory와 slot request를 OpenRTB BidRequest와 내부 AuctionRequest로 변환한다. |
| Auction Flow | deadline, DSP 호출, bid judgment, winner decision을 조율한다. |
| DSP Gateway | DSP별 호출 결과를 `DspCallResult`로 관찰한다. |
| Bid Judge | DSP 결과를 valid candidate와 non-candidate로 분류한다. |
| Winner Selector | 유효 후보 중 winner/no-winner를 결정한다. |

### SSP Ownership Rules

| Concern | Owner |
|---|---|
| 경매 시작 전 요청 거절 | Slot Request Handler |
| BidRequest와 AuctionRequest 정합성 | BidRequest Factory |
| deadline 계산과 적용 | Auction Flow |
| timeout/error/no-bid 관찰 | DSP Gateway |
| invalid bid 판정 | Bid Judge |
| first-price winner 선택 | Winner Selector |

## 4. DSP Components

| Component | Responsibility |
|---|---|
| Bid Handler | OpenRTB BidRequest를 DSP 내부 판단 문맥으로 정규화한다. |
| Campaign Lookup | Campaign Snapshot에서 후보 캠페인을 찾는다. |
| Matcher | 요청과 캠페인의 조건이 맞는지 판단한다. |
| Pricing | 입찰 가능 가격을 계산하거나 no-bid reason을 남긴다. |
| Bid Builder | 확정된 bid 판단을 OpenRTB BidResponse로 만든다. |

DSP는 BidRequest 처리 중 Campaign Data Store를 동기 조회하지 않는다. hot path는 준비된 Campaign Snapshot 또는 index를 읽는다.

## 5. Package Boundary

패키지는 기술 계층보다 runtime component를 드러내는 방향으로 둔다.

| Component group | Package direction |
|---|---|
| SSP components | `com.bbororo.rtb.ssp.<component>` |
| DSP components | `com.bbororo.rtb.dsp.<component>` |
| External adapters | `com.bbororo.rtb.<app>.adapter..` |
| Shared OpenRTB model | `com.bbororo.rtb.shared..` |

패키지 이름은 C3 컴포넌트 이름과 완전히 같을 필요는 없지만, 의존 방향은 컴포넌트 책임을 따라야 한다.

## 6. Failure Classification

| State | Owner | Winner candidate? |
|---|---|---|
| `NO_BID` | DSP Gateway / Bid Judge | No |
| `TIMEOUT` | DSP Gateway | No |
| `LATE_BID` | DSP Gateway / Bid Judge | No |
| `ERROR` | DSP Gateway | No |
| `INVALID_BID` | Bid Judge | No |
| Valid bid | Bid Judge | Yes |

실패 분류의 목적은 원인 설명과 winner candidate 보호다. 모든 실패를 `ERROR`로 합치지 않는다.

## 7. Testing Strategy

| Test type | Purpose |
|---|---|
| Unit test | 컴포넌트별 불변조건 검증 |
| Component test | SSP/DSP 내부 메시지 계약 검증 |
| E2E smoke test | Provider Slot Request부터 AuctionResult까지 happy path 검증 |
| Failure scenario test | timeout, late bid, invalid bid, no-bid, no-winner 검증 |
| Architecture test | 모듈/패키지 의존 방향 검증 |
| Load test | p95/p99, deadline compliance, result correctness 관찰 |

테스트는 “빠르다”보다 “의미가 깨지지 않는다”를 먼저 확인한다. 성능 최적화는 correctness baseline 이후에 한다.

## 8. Performance Interpretation

성능 지표는 다음 순서로 본다.

1. AuctionResult correctness
2. deadline compliance
3. p95/p99 latency
4. timeout/late/invalid/no-winner distribution
5. throughput
6. runtime saturation

처리량 수치만으로 성공을 판단하지 않는다. RTB에서는 높은 RPS보다 제한 시간 안에서 올바른 winner/no-winner를 반환하는 것이 먼저다.

## 9. Deferred Decisions

| Decision | Why deferred |
|---|---|
| External inventory/campaign store product | source of truth와 serving copy 경계가 먼저다. |
| Redis/Valkey or process memory serving copy | freshness, rebuild, failure model이 먼저다. |
| Event output | event identity와 idempotency가 먼저다. |
| Money/ledger | strong consistency와 reconciliation 설계가 먼저다. |
| Advanced DSP routing | baseline fan-out 성능과 failure behavior가 먼저다. |
