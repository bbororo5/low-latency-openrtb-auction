# Workload, Data and Verification Profile

상태: Baseline 1.0

상위 요구사항: [Product and Domain Requirements](product-and-domain-requirements.md)

이 문서는 Requirements를 검증할 수 있도록 business volume, request distribution, dataset, event mix, latency, failure stimulus와 pass condition을 고정한다. 특정 architecture를 정당화하려고 수치를 역산하지 않으며 replica 수, traffic distribution, state store는 ASR/ADR에서 결정한다.

## 1. Evidence Level and Non-claims

### External calibration

- OpenRTB 2.6은 bid price를 CPM으로 표현하지만 실제 거래는 unit impression이며 win notice와 billing notice를 구분한다. [IAB OpenRTB 2.6](https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/2.6.md#423---object-bid)
- Kakao AdX 공개 BidRequest는 `tmax=180ms`를 협의 가능한 기본값으로 제시한다. 국내 S2S RTB deadline의 reference이지 모든 사업자의 latency SLO는 아니다. [Kakao AdX Bid Specification](https://kakaobusiness.gitbook.io/main/partner/kakaoadx_rtb/spec#object-bid-request)
- 공개 iPinYou dataset의 training table은 여러 advertiser/campaign 기간에 약 64.75M bids를 포함하고, 논문은 대형 DSP가 일일 billion-level requests를 볼 수 있다고 설명한다. 규모 교정에만 사용하며 `10M/day`를 이 dataset에서 환산하지 않는다. [iPinYou RTB Benchmark](https://arxiv.org/pdf/1407.7073)

### Explicit non-claims

- `10M auctions/day`는 특정 국내 회사의 실측치가 아니라 portfolio business scenario다.
- busy-window, bid/win/billing 비율은 reproducible synthetic distribution이지 업계 평균이 아니다.
- `50ms`는 healthy path의 internal engineering SLO이며 업계 공통 deadline이 아니다.
- `1,000 RPS`는 expected peak가 아니라 burst/recovery 시험 강도다.
- fault profile 통과는 monthly availability나 region DR을 증명하지 않는다.

## 2. Business Traffic Model

### 2.1 Daily and Busy-window Volume

```text
daily auctions = 10,000,000
daily average  = 10,000,000 / 86,400
               = 115.74 provider RPS

busy-window assumption = daily traffic의 50%가 busiest 4 hours에 집중
busy-window average    = 5,000,000 / 14,400
                       = 347.22 provider RPS
expected design peak   = 400 provider RPS
```

평균에 임의의 peak multiplier를 곱하지 않고 traffic concentration을 명시적 business assumption으로 둔다.

### 2.2 Engineering Targets

| Level | Provider rate | Duration | Meaning |
|---|---:|---:|---|
| Daily average | 115.74 RPS | 24 h equivalent | business volume 산술 평균 |
| Expected design peak | 400 RPS | busiest 4 h | expected service demand |
| Steady acceptance | 500 RPS | 10 min | design peak 대비 25% capacity margin |
| Burst acceptance | 1,000 RPS | 60 s | steady target의 2배에서 correctness와 recovery 검증 |

10M requests는 500 RPS에서도 5시간 33분 20초가 걸린다. v1 acceptance는 24-hour soak 대신 steady/burst/failure profile과 deterministic reconciliation을 조합한다.

## 3. Logical Request and Event Amplification

reference auction은 `Our DSP`, `External DSP A`, `External DSP B` 세 logical bidder를 선택한다.

| Provider RPS | SSP outbound bid requests/s | Our DSP inbound RPS |
|---:|---:|---:|
| 115.74 | 347.22 | 115.74 |
| 400 | 1,200 | 400 |
| 500 | 1,500 | 500 |
| 1,000 | 3,000 | 1,000 |

SSP는 하나의 logical Our DSP에 auction당 한 번 요청한다. 물리 replica가 늘어도 logical request count는 변하지 않는다.

section 5의 mix를 적용한 자사 DSP 작업량은 다음과 같다.

| Provider RPS | Campaign lookups/s | Exposure opens/s | Win events/s | Billing events/s | Expiry events/s |
|---:|---:|---:|---:|---:|---:|
| 500 | 500 | 300 | 100 | 90 | 10 |
| 1,000 | 1,000 | 600 | 200 | 180 | 20 |

## 4. Reference Serving Dataset

| Data | Cardinality | Required distribution |
|---|---:|---|
| Publishers/providers | 1,000 | provider별 1~100 placements |
| Placements | 10,000 | 20% placement가 80% request를 받는 skew |
| Advertiser accounts | 10,000 | account currency KRW |
| Campaigns | 100,000 total | 30,000 active, 나머지는 paused/expired/not-yet-active |
| Creatives | 200,000 | campaign당 평균 2개, banner 중심 + video P1 corpus |
| Daily impression goals | active campaign마다 100~10,000 | 동일 노출 수·다른 목표와 동일 달성률 fixture 포함 |
| Active candidates | request별 p50 20 / p95 200 / p99 1,000 | generator가 percentile을 검증 |
| Hot budget keys | account 상위 1% | Our DSP bid/money operation의 20% 집중 |

### 4.1 Contextual Matching

- active time window
- publisher/placement allow-list
- media type과 creative constraints
- content category allow/block
- campaign CPM과 request floor
- account/campaign money eligibility

개인 식별자, behavior segment, CTR/CVR prediction feature는 생성하지 않는다.

### 4.2 Money Values

| Item | Profile |
|---|---|
| Currency | KRW |
| Bid CPM set | 500 / 1,000 / 1,500 / 2,000 / 3,000 KRW |
| Account funded amount | 1M~100M KRW deterministic distribution |
| Campaign lifetime limit | account funded amount 이하 |
| Campaign daily limit | 100K~5M KRW |
| Near-exhaustion set | account/active campaign의 1%를 1~10 impression cost만 남도록 구성 |
| Event window | 5 minutes; expiry는 virtual clock으로 검증 |

## 5. Request, Event and Fault Mix

### 5.1 Healthy Mix

| Item | Distribution |
|---|---|
| Performance request | banner 300×250, one impression, KRW, first-price |
| Functional corpus | banner 90%, video 10% |
| Our DSP | bid 60%, no-bid 40% |
| External DSP A | bid 70%, no-bid 30% |
| External DSP B | bid 40%, no-bid 60% |
| Our DSP auction win | 전체 auction의 20% |
| Billable conversion | Our DSP win의 90% |
| Non-billable win | Our DSP win의 10%; expiry로 exposure release |

500 RPS를 10분 실행하면 300,000 auctions, 900,000 logical DSP calls, Our DSP win 약 60,000건, billing 약 54,000건이다.

### 5.2 Money Fault Mix

| Fault | Injection rate | Expected result |
|---|---:|---|
| Duplicate win | Our DSP wins의 5% | state/money effect 1회 |
| Duplicate billing | billing의 5% | committed spend 증가 1회 |
| Billing before win | Our DSP wins의 1% | event window 안에 win 도착 시 같은 final state |
| Duplicate loss | losses의 5% | exposure release 1회 |
| Loss + billing conflict | bids의 0.1% | `MONEY_EVENT_CONFLICT`, silent overwrite 0 |
| Missing terminal event | Our DSP wins의 10% | event window 뒤 exposure release |

### 5.3 DSP Fault Mix

| Profile | Behavior |
|---|---|
| Healthy | 각 DSP processing 2~10ms, response p99 ≤ 20ms |
| Partial failure | External DSP B 5% error |
| Timeout | External DSP B가 `effectiveTmax + 300ms` 뒤 응답 |
| Invalid bid | External DSP B bid의 5%가 wrong imp/currency/below-floor |

## 6. Service Objectives

### 6.1 Deadline and Latency

| Path | Target | Meaning |
|---|---:|---|
| Bid cutoff | `effectiveTmax=180ms` | Kakao 공개값을 채택한 reference integration deadline |
| Healthy provider path | p99 ≤ 50ms at 500 RPS | healthy internal engineering SLO |
| Timeout provider path | p99 ≤ 200ms at 400 RPS | cutoff와 response overhead를 포함한 fault envelope |
| Burst healthy path | p99 ≤ 180ms at 1,000 RPS | burst 중 deadline 밖 queueing 방지 |

### 6.2 Availability and Recovery

| Failure | Objective |
|---|---|
| One SSP instance loss | full outage 0; 10s 안에 success rate ≥99.9% 회복 |
| One Our DSP instance loss | 전체 DSP endpoint outage 0; 10s 안에 success rate ≥99.9% 회복 |
| One AZ loss | SSP/Our DSP full outage 0; 30s 안에 success rate ≥99.9% 회복 |
| Rolling deployment | full outage 0; money invariant violation 0 |
| Acknowledged money effect | RPO 0; failover 후 lost/duplicate effect 0 |
| Overload recovery | 부하 감소 후 30s 안에 healthy latency/saturation 범위로 회복 |

`full outage 0`은 fault window의 어떤 1초 bucket도 provider success 0%가 되지 않는다는 뜻이다. 장애 순간 in-flight request 일부는 실패할 수 있으나 correctness와 money invariant는 완화하지 않는다.

## 7. Reference Verification Environment

| Item | Constraint |
|---|---|
| Cloud | AWS `ap-northeast-2`, ephemeral test environment |
| Fault domains | 최소 2 AZ; instance와 AZ fault를 독립 주입 가능 |
| Load generator | target과 분리된 host, 목표 arrival rate 유지 가능 |
| Compute comparison unit | Linux x86_64, application instance당 2 vCPU / 8 GiB |
| Network | private IP; cross-AZ path 포함; TLS cost는 v1 performance에서 제외 |
| Initial representation | OpenRTB 2.6 subset over HTTP POST + JSON |
| Observability | metrics/logs/failure timeline enabled |

정확한 instance 수, placement, LB/Gateway, state store, replication topology는 ADR 결과를 이 section에 반영한 뒤 verification을 실행한다.

## 8. Verification Profiles

### `WP-001` Domain Correctness

- invalid/unsupported request의 pre-fan-out rejection
- on-time valid highest bid winner와 deterministic tie-break
- late/invalid/below-floor/wrong-currency bid 제외
- campaign schedule/media/placement/category/money eligibility
- 최소 `billableImpressionsToday / dailyImpressionGoal`과 stable ID tie-break
- CPM precision과 unit-impression money exactness
- budgetDate, duplicate, reorder, conflict, expiry semantics

Pass: domain check 100%, budget invariant violation 0.

### `WP-002` Healthy Steady

| Item | Value |
|---|---|
| Rate/duration | 500 provider RPS / 10 minutes |
| Volume | 300,000 auctions / 900,000 logical DSP calls |
| Dataset/mix | section 4 full cardinality/skew + section 5.1 |
| Pass | checks 100%, HTTP failure 0%, dropped 0, wrong winner 0, p99 ≤ 50ms, money violation 0 |

### `WP-003` Timeout and Partial DSP Failure

| Item | Value |
|---|---|
| Rate/duration | 400 provider RPS / 5 minutes |
| Stimulus | External DSP B timeout; 별도 run에서 5% error/invalid |
| Pass | checks 100%, provider HTTP failure 0%, late winner 0, terminal count 정확, p99 ≤ 200ms |

### `WP-004` Money Contention and Delivery Faults

| Item | Value |
|---|---|
| Rate/duration | 500 provider RPS / 10 minutes |
| Stimulus | hot budget keys, near-exhaustion, section 5.2 event faults |
| Pass | negative available/budget 초과/duplicate charge 0, valid reorder 동일, conflict 누락 0, exposure leak 0 |

### `WP-005` Burst and Overload Recovery

1. 500 RPS를 2분 유지한다.
2. 1,000 RPS를 60초 적용한다.
3. 100 RPS로 낮추어 60초 유지한다.

Pass: wrong winner와 money violation 0, dropped 0, burst p99 ≤ 180ms, 부하 감소 후 30초 안에 healthy 범위 회복.

### `WP-006` Application Instance Failure

1. 400 RPS healthy traffic을 유지한다.
2. SSP instance 하나를 비정상 종료하고 복구를 관찰한다.
3. 별도 run에서 Our DSP instance 하나를 비정상 종료한다.

Pass: 1초 단위 full outage 0, 10초 안에 success rate ≥99.9%, post-recovery wrong winner 0, acknowledged money effect lost/duplicate 0.

### `WP-007` Availability Zone Failure

1. 400 RPS healthy traffic을 유지한다.
2. 한 AZ의 in-scope application과 state access를 차단한다.
3. 5분 동안 remaining path와 recovery를 관찰한다.

Pass: 1초 단위 full outage 0, 30초 안에 success rate ≥99.9%, post-recovery p99 ≤ 180ms, money RPO 0, invariant violation 0.

### `WP-008` Rolling Deployment

400 RPS에서 SSP와 Our DSP application version을 하나씩 순차 교체한다.

Pass: 1초 단위 full outage 0, 전체 success rate ≥99.9%, wrong winner와 money inconsistency 0, deployment 뒤 healthy p99 회복.

## 9. Evidence Schema

- commit SHA, build/image digest, test profile ID
- region/AZ, instance type, process/replica count, LB/Gateway/state topology
- network path, protocol/representation, JVM options
- dataset version, cardinality, candidate percentile, skew seed
- RPS, duration, logical fan-out, event/fault mix
- request/check/failure/drop과 latency p50/p95/p99
- auction/DSP/campaign/money result distribution
- CPU, heap/GC, thread, queue, connection, in-flight, state-operation latency
- failure injection timestamp, detection/failover/recovery timeline
- test 전후 account/campaign reconciliation checksum

## 10. Ready-for-ASR Criteria

1. traffic 수치와 synthetic assumption이 구분되어 있다.
2. logical fan-out과 physical topology를 혼동하지 않는다.
3. campaign data와 hot budget contention이 실제 pressure를 만든다.
4. deadline, steady, burst, money, instance failure, AZ failure가 독립 scenario로 정의되어 있다.
5. availability requirement가 replica/LB/store solution을 미리 선택하지 않는다.
6. 모든 pass condition을 자동 test 또는 fault experiment로 옮길 수 있다.
