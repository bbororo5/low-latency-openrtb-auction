# Provider and OpenRTB Interface Contracts

상태: Active 1.0
관련 제약: `C-001`, `C-002`, `C-004`, `C-006`

이 문서는 현재 구현이 지원하는 외부 계약만 정의한다. 전체 OpenRTB 2.6 적합성 문서가 아니다.

## 1. Provider-facing Auction

`POST /publisher/auction`, `Content-Type: application/json`

| Field | Required | Rule |
|---|---:|---|
| `providerId` | yes | inventory key와 일치 |
| `placementId` | yes | enabled placement와 일치 |
| `mediaType` | yes | `banner` 또는 `video` |
| `width`, `height` | banner/video | placement와 호환되는 양수 |
| `mimes`, `minDuration`, `maxDuration`, `protocols` | video | placement constraint와 호환 |
| `tmax` | no | 양수 밀리초; 없으면 placement default |

정상적으로 해석된 요청은 경매 거절도 domain outcome으로 표현하므로 HTTP 200을 반환한다.

| Domain status | Meaning |
|---|---|
| `WINNER` | valid candidate 중 winner 존재 |
| `NO_WINNER` | 정상 경매지만 valid candidate 없음 |
| `INVALID_REQUEST` | 필수 값 또는 값 형식이 잘못됨 |
| `UNSUPPORTED_REQUEST` | 계약상 유효할 수 있으나 현재 subset 밖 |

malformed JSON은 HTTP 400, 미지원 method는 405, 처리되지 않은 내부 오류는 5xx다.

## 2. SSP-to-DSP OpenRTB Subset

`POST /openrtb/bid`, HTTP/JSON, one impression, USD, first-price만 지원한다.

| Object | Supported fields |
|---|---|
| `BidRequest` | `id`, `imp`, `tmax`, `at=1` |
| `Imp` | `id`, exactly one of `banner`/`video`, `bidfloor`, `bidfloorcur=USD` |
| `Banner` | `w`, `h` |
| `Video` | `w`, `h`, `mimes`, `minduration`, `maxduration`, `protocols` |
| `BidResponse` | `id`, `seatbid`, `cur` |
| `Bid` | `id`, `impid`, `price`, `cid`, `crid`, `adomain`, `mtype`, `adm` |

DSP response mapping:

| HTTP/wire observation | Terminal classification |
|---|---|
| 200 + valid decodable bid response | `VALID_BID` after judge validation |
| 200 + malformed/invalid response | `INVALID_BID` |
| 204 | `NO_BID` |
| cutoff 전 미완료, client timeout, cancellation, cutoff 이후 관찰 | `TIMEOUT` |
| 기타 HTTP status 또는 transport/application failure | `ERROR` |

## 3. Deadline Propagation

- provider `tmax` 또는 placement default는 `effectiveTmax`다.
- SSP global `bidCutoff = receivedAt + effectiveTmax`다.
- DSP payload의 `tmax`는 template 값을 그대로 복사하지 않고 fan-out 직전 남은 시간을 정수 밀리초로 계산한 `outboundTmax`다.
- 모든 선택 DSP는 같은 serialized payload와 같은 `outboundTmax`를 받는다.
- fan-out 시점에 global deadline이 끝났으면 DSP 호출을 시작하지 않는다.
- SSP의 global cutoff가 DSP가 이해하는 budget보다 항상 우선한다.

## 4. Provider Result Summary

`dspResultCounts`는 다음 다섯 필드만 가진다.

| Field | Terminal result |
|---|---|
| `validBidCount` | `VALID_BID` |
| `noBidCount` | `NO_BID` |
| `timeoutCount` | `TIMEOUT` |
| `invalidBidCount` | `INVALID_BID` |
| `errorCount` | `ERROR` |

다섯 값은 상호 배타적이며 합은 실제로 시작된 DSP call 수와 같다. `LATE_RESPONSE`가 향후 관측되더라도 이 summary를 변경하지 않는다.
