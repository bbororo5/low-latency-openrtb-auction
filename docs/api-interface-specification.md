# API / Interface Specification

이 문서는 RTB 입찰 시스템의 경계 계약을 정의한다. OpenRTB 표준 객체와 프로젝트 전용 객체를 구분하고, 각 경계에서 어떤 요청을 받고 어떤 실패를 반환하는지 정리한다.

## 1. Scope

현재 API 범위는 다음 네 경계다.

| Flow | Contract | Type |
|---|---|---|
| `Provider Slot Client -> SSP` | `ProviderSlotRequest` | Project API |
| `SSP -> DSP` | `BidRequest` | OpenRTB subset |
| `DSP -> SSP` | `BidResponse` or `No-Bid` | OpenRTB subset |
| `SSP -> Provider Slot Client` | `AuctionResult` | Project API |

OpenRTB 표준 계약은 SSP-DSP 경계에만 적용한다. provider-facing 입력과 provider-facing 결과는 이 프로젝트를 실행하고 검증하기 위한 프로젝트 계약이다.

## 2. API Paths

| API path | Purpose | Status |
|---|---|---|
| `POST /publisher/auction` | provider-facing 기본 경매 요청 | primary |
| `POST /openrtb/auction` | 이미 만들어진 OpenRTB BidRequest 검증/벤치마크 경로 | compatibility / benchmark |
| `POST /bid` | 경량 DSP가 SSP의 OpenRTB BidRequest를 받는 경로 | internal project boundary |

`/publisher/auction`은 기본 provider-facing 입력이다. `/openrtb/auction`은 이전 성능 측정과 OpenRTB 호환 검증을 위한 보조 경로다.

## 3. ProviderSlotRequest

`ProviderSlotRequest`는 OpenRTB 객체가 아니다. 광고 슬롯이 열렸다는 사실과 provider/placement, 광고 타입, 슬롯 조건을 SSP에 전달하는 프로젝트 API다.

Supported fields:

| Field | Required | Meaning |
|---|---:|---|
| `providerId` | Y | publisher/provider 식별자 |
| `placementId` | Y | provider 내부 광고 슬롯 식별자 |
| `mediaType` | Y | `banner` 또는 `video` |
| `width` | banner Y, video N | 배너 또는 동영상 슬롯 너비 |
| `height` | banner Y, video N | 배너 또는 동영상 슬롯 높이 |
| `mimes` | video Y | 요청 가능한 동영상 MIME 목록 |
| `minDuration` | video Y | 허용 동영상 최소 길이 |
| `maxDuration` | video Y | 허용 동영상 최대 길이 |
| `protocols` | video Y | 지원 동영상 protocol 목록 |
| `tmax` | N | 응답 제한 시간 힌트 |

검증 규칙:

- `providerId`, `placementId`, `mediaType`이 없으면 `INVALID_REQUEST`다.
- 알 수 없는 placement, 비활성 placement, placement와 다른 광고 타입은 `UNSUPPORTED_REQUEST`다.
- provider-facing 경로는 `banner`와 `video`만 지원한다.
- `native`, `audio`, `pmp`, CTV pod 요청은 `UNSUPPORTED_REQUEST`다.
- banner는 양수 `width`, `height`가 있어야 하며 placement 크기와 일치해야 한다.
- video는 `mimes`, `minDuration`, `maxDuration`, `protocols`가 있어야 하며 placement 조건과 호환되어야 한다.
- `tmax`가 있으면 양수여야 한다.

## 4. SSP To DSP BidRequest

`BidRequest`는 SSP가 DSP에게 보내는 OpenRTB 입찰 요청이다. 기본 경로에서는 SSP가 `ProviderSlotRequest`와 `InventoryPlacement`를 조합해 생성한다.

지원 범위:

- 하나의 `BidRequest`는 정확히 하나의 `Imp`를 가진다.
- `Imp`는 `banner`, `video` 중 정확히 하나의 광고 타입 객체를 가진다.
- 통화는 `USD`만 지원한다.
- `site`, `app`, `user`, `device`, identity signal은 초기 provider-facing 경로에서 생성하지 않는다.
- `native`, `audio`, `pmp`, multi-imp 요청은 provider-facing 경로에서 지원하지 않는다.

Core fields:

| Object | Field | Required | Purpose |
|---|---|---:|---|
| `BidRequest` | `id` | Y | 경매 요청 식별자 |
| `BidRequest` | `imp` | Y | 광고 노출 기회. 현재는 1개만 허용 |
| `BidRequest` | `tmax` | N | 응답 제한 시간 |
| `BidRequest` | `at` | N | 경매 방식. 없으면 first-price |
| `Imp` | `id` | Y | impression 식별자 |
| `Imp` | `bidfloor` | N | 최소 입찰가 |
| `Imp` | `bidfloorcur` | N | 최소 입찰가 통화. 값이 있으면 `USD` |

Media-specific fields:

| Type | Field | Required | Purpose |
|---|---|---:|---|
| `banner` | `w`, `h` | Y | 배너 크기 매칭 |
| `video` | `mimes` | Y | 지원 가능한 MIME 타입 |
| `video` | `minduration`, `maxduration` | Y | 허용 가능한 재생 시간 |
| `video` | `protocols` | Y | 지원 가능한 동영상 응답 프로토콜 |

검증 규칙:

- `BidRequest.id` 또는 `Imp.id`가 없으면 `INVALID_REQUEST`다.
- `imp`가 없거나 1개가 아니면 `UNSUPPORTED_REQUEST`다.
- `Imp`가 지원 광고 타입 객체를 하나도 갖지 않으면 `UNSUPPORTED_REQUEST`다.
- `Imp`가 지원 광고 타입 객체를 두 개 이상 가지면 `INVALID_REQUEST`다.
- 광고 타입별 필수 필드가 없으면 `INVALID_REQUEST`다.
- `bidfloorcur`가 있고 `USD`가 아니면 `UNSUPPORTED_REQUEST`다.

## 5. DSP To SSP BidResponse / No-Bid

경량 DSP는 `BidRequest`를 평가한 뒤 `BidResponse` 또는 no-bid를 반환한다.

BidResponse fields:

| Object | Field | Required | Purpose |
|---|---|---:|---|
| `BidResponse` | `id` | Y | 원본 `BidRequest.id` |
| `BidResponse` | `seatbid` | Y | 입찰 묶음 |
| `BidResponse` | `cur` | N | 입찰 통화. 값이 있으면 `USD` |
| `SeatBid` | `seat` | N | 경량 DSP 또는 광고주 seat 식별자 |
| `SeatBid` | `bid` | Y | 현재는 1개만 사용 |
| `Bid` | `id` | Y | 입찰 식별자 |
| `Bid` | `impid` | Y | 원본 `Imp.id` |
| `Bid` | `price` | Y | CPM 기준 입찰가 |
| `Bid` | `cid` | N | 캠페인 식별자 |
| `Bid` | `crid` | N | 광고 소재 식별자 |
| `Bid` | `adomain` | N | 광고주 도메인 |
| `Bid` | `mtype` | Y | 배너 `1`, 동영상 `2` |
| `Bid` | `adm` | video Y | 동영상 응답 광고 마크업 |

SSP 검증 규칙:

- `BidResponse.id`는 원본 `BidRequest.id`와 같아야 한다.
- `Bid.impid`는 원본 `Imp.id`와 같아야 한다.
- `Bid.price`는 원본 `Imp.bidfloor` 이상이어야 한다.
- `cur`가 있으면 `USD`여야 한다.
- `mtype`은 원본 요청의 광고 타입과 일치해야 한다.
- 동영상 응답은 `adm`을 가져야 한다.

No-bid:

- HTTP 204는 정상 no-bid다.
- no-bid는 실패가 아니며 `NO_BID`로 분류한다.
- timeout과 late bid는 DSP가 반환하는 값이 아니라 SSP가 deadline 기준으로 관찰해 분류하는 상태다.

## 6. AuctionResult

`AuctionResult`는 OpenRTB 표준 객체가 아니다. SSP가 여러 DSP 응답을 수집하고 winner/no-winner를 결정한 뒤 provider-facing 클라이언트에 반환하는 프로젝트 응답이다.

| Field | Meaning |
|---|---|
| `requestId` | 원본 `BidRequest.id` |
| `impId` | 경매 대상 `Imp.id` |
| `mediaType` | `BANNER` 또는 `VIDEO` |
| `status` | `WINNER`, `NO_WINNER`, `INVALID_REQUEST`, `UNSUPPORTED_REQUEST` |
| `winnerDspId` | 낙찰된 경량 DSP 식별자 |
| `winningBidId` | 낙찰된 `Bid.id` |
| `winningPrice` | 낙찰 응답의 입찰가 |
| `auctionPrice` | 경매 규칙에 따라 결정된 최종 가격 |
| `currency` | `USD` |
| `elapsedMs` | 요청 처리 시작부터 결과 결정까지 걸린 시간 |
| `dspResultCounts` | bid, no-bid, timeout, late bid, invalid bid 개수 |

상태 의미:

| Status | Meaning |
|---|---|
| `WINNER` | 유효한 bid 후보 중 winner가 결정됐다. |
| `NO_WINNER` | 경매는 정상 종료됐지만 유효한 bid 후보가 없다. |
| `INVALID_REQUEST` | 요청 구조나 필수 필드가 잘못됐다. |
| `UNSUPPORTED_REQUEST` | 요청은 해석 가능하지만 현재 지원 범위 밖이다. |

`NO_WINNER`는 system error가 아니다. 경매는 정상적으로 끝났지만 낙찰 가능한 bid가 없었던 결과다.

## 7. Internal SSP Message Contracts

| From | Message | To | Contract |
|---|---|---|---|
| Web Adapter | `ProviderSlotRequest` | Slot Request Handler | provider-facing slot request다. OpenRTB 객체로 취급하지 않는다. |
| Slot Request Handler | `AcceptedSlotRequest(AuctionCommand)` | Auction Flow | 경매를 실행할 수 있는 불변 실행 컨텍스트가 만들어졌다. |
| Slot Request Handler | `RejectedSlotRequest` | Web Adapter | 경매 시작 전 요청이 거절되었으며 DSP를 호출하지 않는다. |
| Auction Flow | `BidRequest + Deadline` | DSP Gateway | 지정된 제한 시간 안에서 모든 대상 DSP에 같은 OpenRTB BidRequest를 보낸다. |
| DSP Gateway | `List<DspCallResult>` | Auction Flow | DSP별 호출 관찰 결과다. 아직 낙찰 후보가 아니다. |
| Auction Flow | `AuctionRequest + DspCallResult[] + Deadline` | Bid Judge | 원 요청과 deadline 기준으로 DSP 결과를 분류하고 검증한다. |
| Bid Judge | `JudgementResult` | Auction Flow | 유효 후보와 no-bid/timeout/late/error/invalid 집계를 반환한다. |
| Auction Flow | `List<ValidBidCandidate>` | Winner Selector | Bid Judge를 통과한 후보만 전달한다. |
| Winner Selector | `AuctionOutcome` | Auction Flow | winner 또는 no-winner 판단을 반환한다. |
| Auction Flow | `AuctionResult` | Web Adapter | provider-facing 프로젝트 응답이다. OpenRTB 응답 객체가 아니다. |

핵심 경계는 `DspCallResult`와 `ValidBidCandidate`를 분리하는 것이다. DSP가 무엇인가를 반환했다는 사실과, 그 반환값이 낙찰 후보가 될 수 있다는 판단은 다른 책임이다.

## 8. Unsupported Or Deferred API Scope

| Item | Handling |
|---|---|
| multi-imp request | `UNSUPPORTED_REQUEST` |
| `audio` request | `UNSUPPORTED_REQUEST` |
| `native` provider-facing request | `UNSUPPORTED_REQUEST` |
| `pmp` request | `UNSUPPORTED_REQUEST` |
| `app` request | `UNSUPPORTED_REQUEST` |
| non-USD currency | `UNSUPPORTED_REQUEST` |
| external DSP no-bid response | normal `NO_BID` |
| win notice / billing notice | out of scope |
| ad rendering markup validation | out of scope |
