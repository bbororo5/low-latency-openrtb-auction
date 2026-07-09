# Architecture Significant Requirements

이 문서는 RTB 입찰 시스템의 구조에 큰 영향을 주는 요구사항을 정리한다. 기능 목록을 반복하지 않고, 품질속성, 제약, 기술 리스크, 측정 가능한 시나리오를 기준으로 설계를 압박하는 요소만 다룬다.

## 1. Scope

현재 ASR은 provider slot request가 경량 SSP에 들어오고, SSP가 OpenRTB BidRequest를 생성해 여러 경량 DSP로 fan-out한 뒤, 제한 시간 안에 유효한 BidResponse만으로 winner/no-winner를 결정하는 hot path를 대상으로 한다.

범위 밖:

- 광고 렌더링
- impression/click/conversion tracking
- billing, settlement, ledger
- 광고 운영 백오피스
- 실제 외부 SSP/DSP 연동
- Kubernetes 운영 검증

## 2. Architecture Drivers

| Driver | Why it matters | Architectural pressure |
|---|---|---|
| Deadline-bound auction | RTB에서는 deadline 이후 도착한 높은 bid도 사용할 수 없다. | deadline을 경매 의미의 경계로 두고, late bid를 winner 후보에서 제외한다. |
| Low tail latency | 평균이 좋아도 p95/p99가 나쁘면 유효 bid를 놓칠 수 있다. | hot path를 작게 유지하고, 외부 원본 저장소 동기 의존을 기본으로 두지 않는다. |
| Partial failure tolerance | 일부 DSP 실패가 전체 경매 실패가 되면 no-winner와 장애를 구분할 수 없다. | DSP별 결과를 `NO_BID`, `TIMEOUT`, `ERROR`, `LATE_BID`, `INVALID_BID`로 분리한다. |
| Auction correctness | 잘못된 bid가 winner가 되면 이후 event/ledger를 신뢰할 수 없다. | Bid Judgment를 Winner Decision 앞에 둔다. |
| Rebuildable serving state | hot path는 빠른 serving copy를 읽지만 원본과 혼동하면 복구 모델이 깨진다. | inventory/campaign source of truth와 serving copy를 분리한다. |
| Observability without business truth leakage | metrics/logs/traces는 장애 분석에 필요하지만 원장이나 event의 대체물이 아니다. | observability와 business event/ledger를 분리한다. |

## 3. Priority Quality Attributes

| Priority | Quality attribute | Domain reason | Architecture rule |
|---|---|---|---|
| 1 | Deadline compliance | deadline 이후 응답은 경매에 사용할 수 없다. | deadline 이후 도착한 응답은 가격과 무관하게 후보에서 제외한다. |
| 2 | Low latency | 광고 요청 처리 시간이 길수록 deadline을 넘길 가능성이 커진다. | ProviderSlotRequest부터 AuctionResult까지 p95/p99를 측정한다. |
| 3 | Auction result consistency | invalid bid가 낙찰되면 경매 결과를 설명할 수 없다. | BidResponse 검증을 winner selection 앞에 둔다. |
| 4 | Failure isolation | 일부 DSP 장애가 전체 경매 장애로 번지면 안 된다. | DSP별 결과를 분리하고, 후보가 없으면 no-winner를 정상 결과로 반환한다. |
| 5 | Observability | 지연, timeout, invalid bid의 원인을 설명할 수 있어야 한다. | latency, deadline compliance, timeout, late bid, invalid bid, no-winner를 측정한다. |

## 4. Quality Attribute Scenarios

| ID | Scenario | Response | Measure |
|---|---|---|---|
| QA-001 | 일부 DSP가 deadline 안에 응답하지 않는다. | 해당 DSP를 `TIMEOUT`으로 분류하고, deadline 안에 관찰된 결과만으로 winner/no-winner를 결정한다. | deadline compliance, auction latency p95/p99, timeout count |
| QA-002 | 가장 높은 bid가 deadline 이후 도착한다. | `LATE_BID`로 분류하고 winner 후보에서 제외한다. | late bid count, winner decision trace |
| QA-003 | BidResponse가 원 요청과 맞지 않는다. | `INVALID_BID`로 분류하고 winner 후보에서 제외한다. | invalid bid count, invalid reason |
| QA-004 | 유효한 bid가 하나도 없다. | 시스템 장애가 아니라 정상 `NO_WINNER` 결과를 반환한다. | no-winner rate, no-bid count, timeout count |
| QA-005 | 동시 요청 수 또는 DSP 수가 증가한다. | 처리량, p95/p99, timeout 비율 변화를 측정한다. | observed throughput, auction latency p95/p99, timeout rate |
| QA-006 | 광고 타입별 지연 영향이 다르다. | media type별 timeout 정책을 분리할 수 있게 둔다. | media type별 p95/p99, timeout ratio |

## 5. Constraints

| Constraint | Impact |
|---|---|
| OpenRTB 전체가 아니라 banner/simple video subset만 다룬다. | multi-imp, native, audio, PMP, deal은 현재 API와 테스트 범위에서 제외한다. |
| Provider-facing input은 OpenRTB BidRequest가 아니다. | SSP가 inventory와 조합해 OpenRTB BidRequest를 생성한다. |
| Hot path는 원본 inventory/campaign store를 매 요청마다 동기 조회하지 않는다. | serving copy/snapshot 경계를 유지한다. |
| Money, ledger, billing은 현재 구현 범위 밖이다. | strong business consistency는 설계 후보로만 남기고 hot path에 넣지 않는다. |
| Event output은 현재 컨테이너나 포트로 구현하지 않는다. | event identity, outbox, duplicate handling은 후속 결정으로 둔다. |

## 6. Risks And Follow-Up Decisions

| Risk | Current handling | Follow-up |
|---|---|---|
| Winner tie-break가 결정적이지 않으면 재현 테스트가 흔들릴 수 있다. | first-price winner rule을 사용한다. | 동일 가격 tie-break를 코드와 테스트로 고정한다. |
| OpenRTB no-bid 표현을 어디까지 허용할지 불명확할 수 있다. | 명시적 no-bid 응답은 정상 처리한다. | empty `seatbid`를 no-bid로 볼지 `api-interface-specification.md`와 테스트에서 확정한다. |
| Malformed response를 gateway error로 볼지 invalid bid로 볼지 경계가 애매하다. | 현재는 구현 관찰값 기준으로 분류한다. | DSP Gateway와 Bid Judge의 책임 경계를 테스트로 고정한다. |
| Serving copy freshness 정책이 없다. | source of truth와 serving copy를 분리한다. | stale 허용 범위와 refresh/cutover 정책을 별도 설계한다. |

## 7. Verification

ASR 검증은 기능 성공 여부보다 품질속성 관찰 가능성에 초점을 둔다.

| Requirement | Verification |
|---|---|
| deadline 이후 bid 제외 | late bid 테스트와 winner decision 검증 |
| invalid bid 제외 | request id, imp id, floor, currency, media mismatch 테스트 |
| no-winner 정상 처리 | all no-bid/all timeout/all invalid 시나리오 테스트 |
| latency 관찰 | p50/p95/p99와 deadline compliance 측정 |
| partial failure 격리 | 일부 DSP timeout/error가 전체 500으로 번지지 않는지 확인 |
