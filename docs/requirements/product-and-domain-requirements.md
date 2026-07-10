# Product and Domain Requirements

상태: Baseline 1.0

수치 입력: [Workload, Data and Verification Profile](workload-data-and-verification-profile.md)

규범 수준: 이 문서의 `G`, `FR`, `QR`, `C`, `A`, `OOS`가 ASR과 Architecture Driver의 원천이다.

이 프로젝트는 운영형 광고 플랫폼 전체를 복제하지 않는다. 대신 SSP 경매, 자사 DSP의 campaign serving, 광고주 금액, 장애 중 서비스 지속처럼 실제 구조와 품질을 바꾸는 핵심 경로를 구현한다. 관리 UI, 사용자 프로파일링, ML 최적화, 대외 정산 운영은 제외한다.

## 1. Product Goals

| ID | Goal | Success evidence |
|---|---|---|
| `G-001` | 제한 시간 안에서 여러 논리적 DSP의 응답을 다루고 올바른 경매 결과를 만든다. | 늦거나 잘못된 bid가 winner가 되지 않고 부분 DSP 실패가 전체 경매 실패로 전파되지 않는다. |
| `G-002` | 자사 DSP가 실제 campaign serving data로 입찰 여부와 가격을 결정한다. | 요청 조건과 금액 조건에 맞는 campaign/creative만 선택하고, 적격 후보가 없으면 정상 no-bid한다. |
| `G-003` | 동시 입찰과 중복·지연 money event에서도 광고주와 campaign의 가용 금액을 보존한다. | budget invariant와 idempotent money effect를 모든 기능·부하·장애 시나리오에서 만족한다. |
| `G-004` | application instance 또는 한 AZ가 중단되어도 SSP와 자사 DSP 서비스를 계속 제공한다. | single-instance/AZ fault profile에서 전체 중단이 없고 money state가 보존된다. |
| `G-005` | deadline, campaign lookup, money consistency, availability가 만드는 성능 압력을 측정하고 설명한다. | reference workload에서 latency, 처리량, saturation, correctness, failover를 함께 제시한다. |
| `G-006` | wire 최적화가 도메인 의미를 바꾸지 않게 한다. | JSON과 향후 binary representation이 동일 OpenRTB subset과 경매·금액 계약을 통과한다. |

최대 RPS 하나만으로 성공을 판정하지 않는다. auction correctness, budget correctness, deadline, tail latency, failure continuity, recovery를 함께 검증한다.

## 2. Product Boundary

### 2.1 Roles

| Role | Responsibility in this product | Boundary |
|---|---|---|
| Provider | 광고 기회와 허용 deadline을 SSP에 전달하고 경매 결과를 받는다. | 외부 actor |
| SSP | 요청 검증, logical DSP fan-out, bid 검증, first-price winner 결정, win/loss/billing 연결을 담당한다. | system-of-interest; 단일 instance/AZ 장애 허용 필요 |
| Our DSP | 광고주·campaign·creative를 기준으로 bid/no-bid를 결정하고 budget exposure와 spend를 통제한다. | system-of-interest; 하나의 논리적 DSP, 단일 instance/AZ 장애 허용 필요 |
| External DSP | 서로 다른 회사를 나타내며 독립적으로 bid/no-bid/failure를 반환한다. | 내부 구조와 금액은 불투명 |
| Advertiser/Seat | 자사 DSP에 campaign과 예산을 맡기고 impression 구매 의사를 낸다. | 자사 DSP의 고객 |
| Billable-event source | 낙찰 광고가 과금 가능한 impression이 되었음을 알린다. | 실제 renderer 대신 server-side 계약과 test fixture만 포함 |

`DSP`는 논리적 bidder를 뜻한다. SSP와 자사 DSP의 replica 수, traffic distribution, Gateway/LB, state replication과 저장소는 이 문서에서 선택하지 않는다.

### 2.2 Included

- provider-facing 광고 슬롯 요청 수신과 검증
- one-impression OpenRTB 2.6 subset `BidRequest` 생성
- 자사 DSP와 외부 DSP를 포함한 복수 logical bidder fan-out
- bid, no-bid, timeout, invalid response, transport/application error 분류
- on-time bid의 campaign/creative, price/floor, currency, media 적합성 검증
- deterministic first-price winner 또는 정상 no-winner 결정
- 자사 DSP의 contextual campaign 검색, delivery-aware 선택, deterministic bid 산정
- advertiser account와 campaign budget의 가용성 판단
- win, loss, billable impression, exposure expiry의 domain 처리
- duplicate/out-of-order money event의 idempotent effect와 conflict 탐지
- application instance/AZ failure 중 service continuity와 money correctness
- auction, DSP, campaign matching, money, saturation, failover 관측

### 2.3 Out of Scope

| ID | Out of scope |
|---|---|
| `OOS-001` | 광고 SDK, browser rendering, click tracking, attribution |
| `OOS-002` | 사용자 식별, 프로파일링, 개인정보 수집과 audience targeting |
| `OOS-003` | inventory/campaign/creative 관리자 UI, 원본 CRUD API, 승인 workflow, 동기화 control plane |
| `OOS-004` | CTR/CVR 예측, ML bid optimization, frequency cap, 고급 pacing optimization |
| `OOS-005` | 광고 사기·viewability 판정, creative 심사와 brand-safety 운영 |
| `OOS-006` | 환율 변환, 복수 통화 원장, 세금·invoice·대외 정산·환불·회계 reconciliation |
| `OOS-007` | 외부 partner onboarding, 계약 적합성 인증, public internet 보안 운영 |
| `OOS-008` | 외부 DSP 회사 내부의 campaign, budget, gateway, replica 구조 |
| `OOS-009` | region 전체 장애를 견디는 multi-region DR, Kubernetes 운영 플랫폼, autoscaling control plane |

원본 관리가 범위 밖이어도 serving data와 mutable budget state는 범위 안이다. 작은 고정 응답 fixture만으로 campaign bidding이나 money correctness를 대체할 수 없다.

## 3. Canonical Domain Semantics

### 3.1 Time and Auction Boundary

- `receivedAt`: SSP가 provider request 처리를 시작하고 body를 해석하기 전의 시각
- `effectiveTmax`: 요청의 양수 `tmax`, 없으면 reference profile의 기본값
- `bidCutoff`: `receivedAt + effectiveTmax`
- `dispatchAt`: SSP가 검증과 정규화를 마치고 DSP payload를 확정한 시각
- `outboundTmax`: `dispatchAt`에서 `bidCutoff`까지 남은 양의 정수 millisecond
- `on-time response`: SSP가 `bidCutoff` 이하에 수신한 DSP response
- `provider latency`: provider request 시작부터 response body 수신 완료까지

`bidCutoff`는 winner 후보 자격의 경계다. provider latency SLO와 DSP wire deadline을 같은 숫자로 간주하지 않는다.

### 3.2 Auction and DSP Results

| Layer | Values | Rule |
|---|---|---|
| Auction outcome | `WINNER`, `NO_WINNER`, `INVALID_REQUEST`, `UNSUPPORTED_REQUEST` | 한 요청은 하나의 outcome만 가진다. |
| DSP terminal result | `VALID_BID`, `NO_BID`, `TIMEOUT`, `INVALID_BID`, `ERROR` | 호출한 logical DSP마다 cutoff 시점에 정확히 하나를 가진다. |
| Post-cutoff diagnostic | `LATE_RESPONSE` | 이미 반환된 result와 winner를 바꾸지 않는 진단 event다. |

`VALID_BID` 중 가장 높은 CPM을 제시한 bid가 first-price winner다. 동가이면 `dspId`, `bidId` 오름차순으로 결정한다.

### 3.3 Campaign Serving

자사 DSP는 다음 순서로 campaign/creative 하나를 선택한다.

1. active schedule, media type, creative size, placement/publisher/category, floor, currency로 후보를 좁힌다.
2. account와 campaign의 money eligibility를 확인한다.
3. 후보마다 deterministic pricing rule을 적용한다.
4. `deliveryRatio = billableImpressionsToday / dailyImpressionGoal`이 가장 낮은 campaign을 선택한다.
5. delivery ratio가 같으면 `campaignId`, 같은 campaign의 creative가 여럿이면 `creativeId` 오름차순으로 결정한다.
6. 적격 후보가 없으면 정상 `NO_BID`를 반환한다.

변경 가능한 campaign 이름과 적재 순서에 따른 index는 tie-break에 사용하지 않는다. 후보 검색 index, data distribution, 저장 형식은 architecture 결정으로 남긴다.

### 3.4 Currency and Price

OpenRTB `BidResponse.cur`는 ISO-4217 통화이고 `Bid.price`는 unit impression 거래를 CPM으로 표현한다. [IAB OpenRTB 2.6 BidResponse/Bid](https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/2.6.md#42---object-specifications)

- reference release의 거래 통화는 `KRW` 하나다.
- bid와 floor는 CPM이며 billable impression 한 건의 명목 금액은 CPM의 `1/1000`이다.
- 허용 CPM 정밀도는 KRW 소수점 셋째 자리까지다. 더 정밀한 값은 묵시적으로 반올림하지 않고 invalid bid로 처리한다.
- 내부 누적·비교는 허용 정밀도를 보존해야 하며 binary floating-point 누적 오차로 budget invariant가 달라지면 안 된다.
- account, campaign, request, bid의 currency가 다르면 bid 자격이 없다.

내부 타입을 `long`, `BigDecimal`, 다른 fixed-point 중 무엇으로 구현할지는 architecture/implementation 결정이다.

### 3.5 Budget and Money Events

OpenRTB에서 win notice는 낙찰을 알릴 뿐 delivery나 billability를 뜻하지 않고, billing notice가 실제 spend 적용 시점을 나타낸다. [IAB OpenRTB win/billing/loss notices](https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/2.6.md#423---object-bid)

- `fundedAmount`: advertiser account가 사용할 수 있는 총 금액
- `campaignLifetimeLimit`: campaign 전체 기간의 지출 상한
- `campaignDailyLimit`: Asia/Seoul 날짜 경계의 일일 지출 상한
- `committedSpend`: billable impression으로 확정된 지출
- `outstandingExposure`: 아직 billable 여부가 끝나지 않아 중복 사용하면 안 되는 잠재 지출

`budgetDate`는 auction `receivedAt`의 Asia/Seoul 날짜다. 자정 전에 생긴 exposure가 자정 뒤 billable되어도 같은 `budgetDate`에 commit한다.

```text
accountCommitted + accountExposure <= fundedAmount
campaignLifetimeCommitted + campaignExposure <= campaignLifetimeLimit
campaignDailyCommitted + campaignDailyExposure <= campaignDailyLimit
available >= 0
```

| Event | Domain effect |
|---|---|
| Bid offered | spend는 아니지만 billable 가능 금액은 outstanding exposure에 포함한다. |
| Win notice | 낙찰을 확정하지만 spend를 증가시키지 않는다. |
| Loss notice | 더는 billable될 수 없는 bid의 exposure를 해제한다. |
| Billable impression | clearing price의 unit-impression cost를 committed spend로 정확히 한 번 반영한다. |
| Exposure expiry | event window가 끝난 미결 bid의 exposure를 해제한다. |

동일 `auctionId + bidId + eventType`의 재전송은 금액에 한 번만 영향을 준다. win과 billing이 순서가 바뀌어도 event window 안에 둘 다 유효하면 final state가 같아야 한다. loss와 billing처럼 모순되는 terminal event는 `MONEY_EVENT_CONFLICT`로 격리한다.

중앙 conditional update, distributed token/quota, escrow, batch accounting 등은 이 불변식을 구현하는 ADR 대안이지 요구사항이 아니다.

### 3.6 Availability and Failure Boundary

- 단일 SSP application instance 장애가 전체 SSP outage를 만들면 안 된다.
- 단일 Our DSP application instance 장애가 자사 DSP 전체 outage를 만들면 안 된다.
- 한 availability zone의 loss가 SSP 또는 자사 DSP 전체 outage를 만들면 안 된다.
- planned rolling deployment는 provider-visible full outage를 만들면 안 된다.
- 장애 전 acknowledge된 money event의 effect는 failover 후 유실되거나 중복 적용되면 안 된다.
- 장애 순간 처리 중이던 auction은 error budget 안에서 실패할 수 있지만 money invariant를 훼손하면 안 된다.
- region 전체 loss는 `OOS-009`다.

정확한 replica 수, health checking, routing, state replication, leader/quorum은 ASR/ADR에서 결정한다.

## 4. Functional Requirements

| ID | Priority | Requirement | Acceptance condition |
|---|---:|---|---|
| `FR-001` | P0 | SSP는 provider, placement, media, currency, deadline을 검증하고 실행 불가능한 요청을 DSP 호출 전에 거절해야 한다. | 거절 시 DSP call 0; invalid와 unsupported가 구분된다. |
| `FR-002` | P0 | SSP는 유효 request와 inventory serving data로 immutable auction context와 OpenRTB request template을 생성해야 한다. | request/imp/media/floor/currency/deadline 의미가 일치한다. |
| `FR-003` | P0 | SSP는 선택한 각 logical DSP에 의미상 동일한 request를 전달해야 한다. | 논리적 retry가 별도 bid나 별도 money exposure로 계산되지 않는다. |
| `FR-004` | P0 | SSP는 cutoff에서 시작된 DSP 호출을 상호 배타적인 terminal result로 분류해야 한다. | terminal result 합이 시작된 logical DSP call 수와 같다. |
| `FR-005` | P0 | SSP는 on-time bid의 request/imp, campaign/creative, CPM/floor, currency, media, markup을 검증해야 한다. | 하나라도 실패하면 `INVALID_BID`이며 winner 후보가 아니다. |
| `FR-006` | P0 | SSP는 valid bid에 deterministic first-price auction을 수행해야 한다. | winner와 clearing price가 도착 순서에 무관하다. |
| `FR-007` | P0 | valid bid가 없으면 정상 `NO_WINNER`를 반환해야 한다. | transport success와 no-winner가 구분된다. |
| `FR-008` | P0 | 한 DSP의 지연·오류가 다른 DSP의 유효 bid 판단을 중단시키면 안 된다. | 부분 DSP 실패 시에도 남은 후보로 정확한 결과를 만든다. |
| `FR-009` | P0 | 자사 DSP는 serving dataset에서 contextual 조건을 만족하는 campaign/creative 후보를 검색해야 한다. | 부적격 candidate가 bid로 반환되는 경우 0건이다. |
| `FR-010` | P0 | 자사 DSP는 money-eligible 후보 중 daily impression 목표 달성률이 가장 낮은 campaign을 stable ID tie-break로 선택하고 deterministic price로 bid해야 한다. | 동일 domain input에서 같은 bid/no-bid와 campaign/creative가 선택된다. |
| `FR-011` | P0 | 동시에 처리되는 bid가 account와 campaign budget을 초과해 잠재 지출을 약속하면 안 된다. | 모든 profile에서 budget invariant violation 0건이다. |
| `FR-012` | P0 | win/loss/billing/expiry event를 순서·중복에 안전한 domain transition으로 처리해야 한다. | duplicate charge 0; valid reorder final state 동일; conflict 분리. |
| `FR-013` | P0 | SSP는 win/loss/billing event를 해당 DSP bid와 연결해야 한다. | auction/bid/campaign/price/currency correlation이 보존된다. |
| `FR-014` | P1 | provider result는 outcome, winner, clearing price, elapsed time, DSP terminal summary를 제공해야 한다. | summary가 상호 배타적이고 logical DSP call 수와 일치한다. |

## 5. Quality Requirements

| ID | Priority | Requirement | Measure |
|---|---:|---|---|
| `QR-001` | P0 | late 또는 invalid bid가 winner가 되면 안 된다. | 모든 profile에서 late/invalid winner 0건. |
| `QR-002` | P0 | healthy provider path는 reference latency와 steady capacity를 만족해야 한다. | `WP-002` pass. |
| `QR-003` | P0 | DSP timeout과 부분 실패는 deadline 안에서 격리되어야 한다. | `WP-003` pass. |
| `QR-004` | P0 | money state는 동시성과 failover에도 budget invariant를 보존해야 한다. | `WP-004`, `WP-006~007`에서 violation 0건. |
| `QR-005` | P0 | money event는 at-least-once와 reorder 상황에서 idempotent effect를 가져야 한다. | duplicate charge 0; valid reorder final state 동일. |
| `QR-006` | P0 | thread, queue, connection, in-flight work, money operation은 유한한 상한과 관측 수단을 가져야 한다. | unbounded resource 금지; saturation signal 제공. |
| `QR-007` | P0 | overload 이후 process restart 없이 정상 처리 상태로 회복해야 한다. | `WP-005` pass. |
| `QR-008` | P0 | 같은 domain input과 valid candidate 집합은 같은 결과를 만들어야 한다. | 순서를 바꾼 반복에서 auction/campaign result 동일. |
| `QR-009` | P0 | 단일 application instance 또는 단일 AZ 장애가 전체 SSP/Our DSP outage를 만들면 안 된다. | `WP-006~007`의 failover와 post-recovery condition 만족. |
| `QR-010` | P0 | 장애 전 acknowledge된 money effect의 RPO는 0이어야 한다. | failover 전후 reconciliation에서 lost/duplicate committed effect 0건. |
| `QR-011` | P1 | rolling deployment는 full outage와 money inconsistency 없이 완료되어야 한다. | `WP-008` pass. |
| `QR-012` | P1 | auction, DSP, campaign lookup, money transition, saturation, failover를 bounded-cardinality signal로 측정해야 한다. | request/bid ID를 metric tag로 사용하지 않고 병목과 failure mode를 구분한다. |
| `QR-013` | P1 | 성능·가용성 evidence는 environment, topology, dataset, event mix, fault injection, correctness를 함께 기록해야 한다. | workload profile의 evidence schema 충족. |
| `QR-014` | P1 | wire representation 변경은 domain contract와 acceptance test를 바꾸지 않아야 한다. | 같은 corpus가 JSON과 후보 binary codec에서 의미상 동일하다. |

## 6. Constraints

| ID | Constraint |
|---|---|
| `C-001` | SSP-DSP wire meaning은 OpenRTB 2.6의 명시적 subset을 사용한다. |
| `C-002` | provider-facing API는 프로젝트 전용 계약이며 OpenRTB object를 직접 노출하지 않는다. |
| `C-003` | baseline auction은 one impression, first-price다. |
| `C-004` | P0 media는 banner, video는 기능 호환성 P1이다. Native, audio, multi-imp, PMP/deal은 제외한다. |
| `C-005` | reference release의 account, campaign, floor, bid, clearing price는 `KRW` 하나를 사용한다. |
| `C-006` | 초기 구현과 검증 runtime은 Java 21이다. |
| `C-007` | reference production scope는 AWS single region이며 single-AZ failure를 견뎌야 한다. Multi-region은 제외한다. |
| `C-008` | SSP와 자사 DSP의 reference deployment에서 단일 application instance는 허용하지 않는다. |

HTTP/JSON은 initial representation이지 영구 제약이 아니다. IAB OpenRTB Protobuf 같은 binary representation과 transport 변경은 같은 domain contract 아래에서 비교한다. [IAB OpenRTB Protobuf](https://github.com/InteractiveAdvertisingBureau/openrtb2.x/tree/main/proto)

## 7. Assumptions

| ID | Assumption | Invalidated when |
|---|---|---|
| `A-001` | 외부 DSP는 독립 회사의 logical endpoint이며 내부 campaign과 money state는 우리 범위 밖이다. | 외부 DSP 내부도 system-of-interest에 포함할 때 |
| `A-002` | inventory와 campaign source-of-truth 관리는 별도 system이며 이 release에는 reproducible serving dataset이 제공된다. | 관리·동기화 control plane을 포함할 때 |
| `A-003` | billable event source는 auction/bid ID와 clearing price/currency를 신뢰 가능한 server-side event로 제공한다. | client-only tracking이나 fraud 판정을 포함할 때 |
| `A-004` | daily budget 날짜 경계는 `Asia/Seoul`이다. | advertiser별 timezone을 지원할 때 |
| `A-005` | test cloud와 failure injection은 architecture 결정이 아니라 비교 가능한 verification input이다. | 실제 production environment 계약이 별도로 주어질 때 |

## 8. Acceptance Source

traffic, dataset, logical fan-out, event mix, latency, failure stimulus, pass condition은 [Workload, Data and Verification Profile](workload-data-and-verification-profile.md)에만 정의한다. Requirements는 replica 수, Gateway/LB, database/cache, partitioning, consensus를 선택하지 않는다.

## 9. Change Policy

1. 요구사항은 business outcome과 measurable quality를 정의하고 solution은 ASR/ADR에서 다룬다.
2. 구현 결과에 맞춰 traffic, latency, availability, correctness 기준을 낮추지 않는다.
3. money invariant나 failure scope를 완화하려면 ADR이 아니라 Requirements부터 변경한다.
4. 새로운 media, multi-currency, refund, user targeting은 scope 변경과 ASR 재검토 후 추가한다.
5. ADR은 이 문서에 없는 business policy를 도입하지 않는다.
