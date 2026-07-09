# SSP Message Contract Research

이 문서는 SSP 5개 컴포넌트의 책임을 확정하기 전에, RTB/OpenRTB 업계 기준으로 협력 메시지와 불변조건을 정합하기 위해 작성한 리서치 노트다.

현재 SSP 메시지 계약의 기준 문서는 `api-interface-specification.md`다. 이 문서는 업계 기준과 설계 근거를 보존하는 참고 자료이며, 구현 계약을 직접 바꾸지 않는다.

목적은 OpenRTB 전체를 구현하는 것이 아니다. IAB Tech Lab OpenRTB 2.6과 대형 구현체 문서를 기준으로, 현재 프로젝트의 SSP hot path에 필요한 메시지 계약만 좁힌다.

## Sources

- IAB Tech Lab OpenRTB 2.6 specification: https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/2.6.md
- IAB Tech Lab OpenRTB 2.x implementation notes: https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/implementation.md
- IAB Tech Lab OpenRTB overview: https://iabtechlab.com/standards/openrtb/
- Google Authorized Buyers OpenRTB integration guide: https://developers.google.com/authorized-buyers/rtb/openrtb-guide

## Industry Baseline

OpenRTB는 구매자와 판매자 사이의 실시간 입찰 요청/응답을 표준화하기 위한 프로토콜이다. IAB Tech Lab 문서는 bid request/response와 win, billing, loss notice 상호작용을 다룬다. 이 프로젝트는 그중 bid request/response hot path만 사용하고 notice 계열은 제외한다.

OpenRTB 기준에서 exchange 또는 SSP는 ad request를 받은 뒤 bidder에게 bid request를 보낸다. Bidder는 bid response 또는 no-bid를 반환한다. 따라서 우리 프로젝트의 `ProviderSlotRequest`는 OpenRTB 표준 객체가 아니라, SSP가 OpenRTB `BidRequest`를 생성하기 전의 provider-facing 프로젝트 입력이다.

HTTP 경계에서는 bid request에 HTTP POST가 요구된다. content가 있는 bid response는 HTTP 200, valid request에 대해 content가 없는 응답은 no-bid 표현 중 하나로 HTTP 204가 가능하다. JSON은 OpenRTB의 기본적이고 널리 쓰이는 표현이고, Protobuf 같은 binary representation도 구현체에 따라 사용된다.

IAB implementation notes는 no-bid 표현으로 HTTP 204, 빈 JSON object, 빈 `seatbid`, no-bid reason을 포함한 빈 `seatbid`를 언급한다. 현재 프로젝트는 DSP HTTP 204를 `NO_BID`로 처리하고, 빈 `seatbid`도 no-bid로 해석할 수 있는 방향이 업계 기준과 맞다.

## Project Scope Decisions

현재 프로젝트는 다음 OpenRTB subset만 사용한다.

| 항목 | 결정 |
|---|---|
| SSP provider-facing input | `ProviderSlotRequest`; OpenRTB 표준 객체 아님 |
| SSP-DSP transport | HTTP POST + JSON |
| BidRequest cardinality | single `Imp` only |
| Supported media | `Imp.banner`, `Imp.video` |
| Excluded media/market features | native, audio, pmp, deal, CTV pod, multi-imp |
| Auction type | first-price only |
| Currency | `USD` only |
| No-bid | HTTP 204 and empty `seatbid` are normal no-bid |
| Notices | win, billing, loss notice excluded |
| User/context signals | site/app/user/device/eids excluded for now |

이 결정은 OpenRTB 표준의 가능 범위를 축소한 것이다. 예를 들어 OpenRTB는 multi-imp, site/app/device/user, PMP, native, audio, notice를 다룰 수 있지만, 현재 프로젝트는 CS hot path와 low-latency fan-out 문제에 집중하기 위해 제외한다.

## Message Flow

현재 SSP main path의 협력 메시지는 다음 순서다.

| From | Message | To | Meaning |
|---|---|---|---|
| HTTP Adapter | `ProviderSlotRequest` | Slot Ingress | provider-facing slot request |
| Slot Ingress | `AcceptedSlotRequest(AuctionCommand)` | Auction Execution | auction is executable |
| Slot Ingress | `RejectedSlotRequest` | HTTP Adapter | request rejected before auction execution |
| Auction Execution | `BidRequest + Deadline` | DSP Gateway | call all configured DSP endpoints |
| DSP Gateway | `List<DspCallResult>` | Auction Execution | observed DSP call outcomes |
| Auction Execution | `AuctionRequest + DspCallResult[] + Deadline` | Bid Judgment | classify and validate DSP outcomes |
| Bid Judgment | `JudgementResult` | Auction Execution | valid candidates and outcome counts |
| Auction Execution | `List<ValidBidCandidate>` | Winner Decision | select winner under auction rule |
| Winner Decision | `AuctionOutcome` | Auction Execution | winner or no-winner |
| Auction Execution | `AuctionResult` | HTTP Adapter | project-level response |

## Message Invariants

### ProviderSlotRequest

`ProviderSlotRequest` is a project contract, not an OpenRTB contract.

Invariants:

- `providerId` and `placementId` identify the supply-side placement.
- `mediaType` must be `banner` or `video`.
- banner requests must carry positive `width` and `height`.
- video requests must carry mimes, duration range, and protocols.
- `tmax`, when present, must be positive.
- unknown placement, disabled placement, unsupported media, and incompatible slot constraints are rejected before auction execution.

### AuctionCommand

`AuctionCommand` is the first internal message that means "the auction can run."

Invariants:

- `bidRequest` is the OpenRTB request sent to DSPs.
- `auctionRequest` is the internal normalized context used by SSP decisions.
- `bidRequest.id == auctionRequest.requestId`.
- `bidRequest.imp` has exactly one item.
- `bidRequest.imp[0].id == auctionRequest.impId`.
- `bidRequest.imp[0]` has exactly one supported media object: banner or video.
- `auctionRequest.mediaType` matches the media object in `bidRequest.imp[0]`.
- `bidfloorcur` is `USD`.
- `auctionType` is first-price.
- `receivedAt` is the timestamp used for deadline and elapsed time.

### DspCallResult

`DspCallResult` is an observation of one DSP endpoint call, not yet a valid bid candidate.

Invariants:

- every result has a `dspId`, `status`, and `receivedAt`.
- `BID_RECEIVED` may carry a `BidResponse`; it still needs Bid Judgment validation.
- `NO_BID` is a normal result, not a failure.
- `TIMEOUT`, `ERROR`, and `LATE_BID` are excluded from winner selection but should be counted separately.
- HTTP 204 maps to `NO_BID`.
- malformed response payload maps to `ERROR` at gateway level or `INVALID_BID` at judgment level; the project should choose one stable classification and document it.

### JudgementResult

`JudgementResult` is the result of validating DSP outcomes against the original auction context.

Invariants:

- `validCandidates` contains only bids that passed deadline, request id, imp id, price, currency, media type, and required markup checks.
- `summary.noBidCount` counts normal no-bid outcomes.
- `summary.timeoutCount`, `summary.lateBidCount`, `summary.errorCount`, and `summary.invalidBidCount` are distinct diagnostic categories.
- a bid that arrived after deadline must not become a valid candidate even if it has the highest price.
- `BidResponse.id` must match the original `BidRequest.id`.
- each valid `Bid.impid` must match the original `Imp.id`.
- bid price must be positive and greater than or equal to `Imp.bidfloor`.
- response currency, when present, must match request currency.
- bid media type must match the requested media.
- video bids must include inline markup or a supported serving path. Current project uses `adm` for video.

### AuctionOutcome

`AuctionOutcome` is the internal result of winner selection.

Invariants:

- winner, when present, must come from `JudgementResult.validCandidates`.
- first-price auction selects the highest bid price.
- first-price `auctionPrice == winningPrice`.
- tie-break is exchange policy, not dictated by OpenRTB. For deterministic tests, this project should use response arrival time and then `dspId`.
- empty candidates produce no winner.

### AuctionResult

`AuctionResult` is a project response, not an OpenRTB object.

Invariants:

- `WINNER` requires request id, imp id, media type, winner DSP id, winning bid id, winning price, auction price, currency, elapsed time, and DSP result counts.
- `NO_WINNER` requires request id, imp id, media type, currency, elapsed time, and DSP result counts, but winner fields are empty.
- `INVALID_REQUEST` and `UNSUPPORTED_REQUEST` may omit request id and DSP counts when rejection happens before `AuctionCommand`.
- `NO_WINNER` is a normal auction result, not HTTP 500.

## Code Alignment Notes

These notes explain where the current code still needs alignment with the message contracts promoted into `api-interface-specification.md`.

| Area | Observation |
|---|---|
| Slot Ingress validation | `DefaultSlotRequestHandler` now coordinates request, inventory, and command creation, while `SlotMediaSpecValidator` owns banner/video compatibility checks with explicit empty/failure results. |
| Winner tie-break | `FirstPriceWinnerSelector` currently compares price only. The documented deterministic tie-break by arrival time then `dspId` is not yet implemented. |
| No-bid classification | HTTP 204 is already mapped to `NO_BID`, aligned with IAB implementation guidance. |
| Empty seatbid | Current `BidJudge` treats empty `seatbid` under `BID_RECEIVED` as invalid. Industry guidance allows empty `seatbid` as no-bid, so the project should either support it or document that only HTTP 204 no-bid is accepted from the lightweight DSP path. |
| Malformed bid response | Gateway maps JSON decode failure to `ERROR`. This is defensible as transport/payload failure, but if the payload came from a reachable DSP, `INVALID_BID` may be more domain-specific. Choose one and test it. |

## Current Use

Use this research note only as supporting context when reviewing or evolving the SSP message contracts in `api-interface-specification.md`.

Do not add new implementation scope directly from this note. Promote an interface decision to `api-interface-specification.md`, an implementation decision to `implementation-technical-specification.md`, or an architectural decision to an ADR first. Then update code only where the current implementation violates the chosen contract.
