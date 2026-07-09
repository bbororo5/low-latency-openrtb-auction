# RTB Data State Architecture

## 1. Purpose

이 문서는 RTB 경매와 입찰에서 등장하는 데이터가 시스템 안에서 어떤 상태로 존재해야 하는지 정의한다.

목적은 필드나 DB 스키마를 먼저 정하는 것이 아니다. 어떤 데이터가 진실원(source of truth)인지, 어떤 데이터가 파생 상태(derived state)인지, 어떤 데이터가 일시 입력(transient input), 비즈니스 이벤트(event), 관측 데이터(observability data)인지 먼저 구분한다.

저장소 제품 선택, SSP/DSP store ownership, budget reservation, event output, 장애 복구, 정합성 모델은 이 분류와 불변조건을 바탕으로 별도 결정한다.

이 문서는 현재 구현 범위를 확장하지 않는다. 현재 구현은 Provider Slot Request를 받아 SSP가 OpenRTB BidRequest를 생성하고, BidResponse 수집과 낙찰 판단까지 이어지는 hot path에 집중한다. 과금, 정산, 리포팅, 광고 운영 백오피스는 여전히 범위 밖이다.

## 2. Design Lens

이 문서는 Martin Kleppmann의 데이터 시스템 관점에 가깝게, 저장소를 제품 단위가 아니라 데이터의 의미와 흐름 단위로 본다.

핵심 질문은 다음 순서로 다룬다.

1. 어떤 데이터가 장기적으로 보존되어야 하는 진실원인가?
2. 어떤 데이터가 진실원에서 만들어진 serving copy 또는 index인가?
3. 어떤 데이터가 요청 처리 중에만 존재하는 transient input 또는 transient decision인가?
4. 어떤 비즈니스 사실을 append-only event로 남길 가치가 있는가?
5. 어떤 데이터가 정확성보다 빠른 조회, 짧은 불일치 허용, 재구성 가능성을 우선하는가?
6. 어떤 데이터가 장애 후 반드시 복구되어야 하며, 어떤 데이터는 재생성해도 되는가?

이 순서를 거치기 전에는 PostgreSQL, Redis/Valkey, Kafka/Redpanda, Kinesis, ClickHouse 같은 제품을 결정하지 않는다.

## 3. State Categories

| Category | Meaning | Typical storage implication |
|---|---|---|
| Source of truth | 시스템이 기준으로 삼는 원본 상태 | 영속 DB, 관리 가능한 기준 저장소, 감사 가능한 변경 이력 |
| Derived serving state | 원본에서 만든 hot-path 조회 상태 | process memory, Redis/Valkey, managed memory store, materialized view |
| Transient input | 요청 처리 중 들어왔다가 사라지는 입력 | 메모리 객체, 필요 시 샘플링 또는 축약 이벤트 |
| Transient decision | 요청 처리 중 계산되는 중간 판단 | 메모리 객체, 필요 시 debug sample |
| Observed external fact | 외부 시스템이 반환하거나 발생시킨 사실 | 검증 전 관찰 결과, audit/event 후보 |
| Business event | 시간이 지나도 의미가 있는 비즈니스 사실 | append-only log, outbox, stream, event store |
| Ledger state | 돈, 차감, 정산의 기준 상태 | 강한 정합성 저장소, 원장, idempotency, reconciliation |
| Observability data | 시스템 상태와 장애 원인 설명 자료 | metrics, logs, traces backend |

## 4. Project Boundary

현재 구현에서 직접 다루는 데이터는 다음이다.

| Data | Current handling | State category |
|---|---|---|
| `ProviderSlotRequest` | provider-facing 요청으로 수신 | Transient input |
| `InventoryPlacement` | 현재는 in-memory catalog에 로드 | Source of truth 후보의 serving copy |
| SSP auction policy | first-price, single-imp, USD, banner/simple video를 코드/문서 규칙으로 적용 | Source rule / serving policy |
| OpenRTB `BidRequest` | SSP가 ProviderSlotRequest와 InventoryPlacement에서 생성 | Derived message |
| `AuctionCommand` | Slot Ingress가 Auction Execution에 넘기는 실행 컨텍스트 | Immutable execution context |
| DSP `CampaignSnapshot` | DSP 시작 시 sample data로 구성 | Source of truth 후보의 serving copy |
| DSP campaign index/repository | CampaignSnapshot을 hot path에서 조회 | Derived serving state |
| Bid decision data | DSP 내부에서 bid/no-bid 판단 중 생성 | Transient decision |
| `DspCallResult` | DSP 호출 결과 관찰값 | Observed external fact |
| `BidResponse` | DSP가 반환한 OpenRTB 응답 | Observed external fact |
| `AuctionResult` | SSP가 검증/판정 후 반환 | Result message / event candidate |
| Observability data | metrics/logs/traces로 수집 | Observability data |

현재 구현에서 직접 다루지 않는 데이터는 다음이다.

| Data | Reason |
|---|---|
| Money state | 계좌, 예산, reservation, 차감은 제품급 money flow 설계가 먼저 필요하다 |
| Real-time control state | pacing, frequency cap, dynamic routing은 현재 hot path 검증 범위 밖이다 |
| Transaction event | win/impression/billing 후속 흐름이 현재 범위 밖이다 |
| Ledger and settlement data | 과금/정산 시스템은 현재 목표가 아니다 |
| Analytics and reporting data | 리포팅 저장소와 집계 파이프라인은 별도 제품 영역이다 |

## 5. Data State Map

이 장은 각 데이터의 비즈니스 의미와 상태 성격을 정리한다. 상세 필드는 Tech Spec에서 다룬다.

### 5.1 Auction Opportunity

광고 노출 기회와 경매 입력을 표현한다. 현재 provider-facing 경로에서는 `ProviderSlotRequest`, SSP inventory placement, SSP가 생성한 OpenRTB `BidRequest`가 함께 이 흐름을 이룬다.

`ProviderSlotRequest`는 광고 슬롯이 열렸다는 사실과 provider/placement, 광고 타입, 슬롯 제약을 표현한다. 요청 자체는 장기 상태의 진실원이 아니다. SSP가 이를 inventory와 조합해 생성한 `BidRequest`는 DSP-facing 파생 메시지다.

| Aspect | Decision |
|---|---|
| Frequency | 경매 요청마다 매우 자주 생성된다 |
| Lifetime | 대부분 요청 처리 중에만 필요하다 |
| Correctness | 요청 검증과 inventory 매칭은 정확해야 한다 |
| Hot path | SSP와 DSP 양쪽 hot path에서 직접 사용된다 |
| Storage | 기본은 메모리 처리다. 사후 분석은 샘플링 또는 축약 이벤트가 적합하다 |

### 5.2 Supply-Side Inventory And Policy

SSP가 provider/placement를 해석하고 경매를 어떻게 열지 결정하는 기준 데이터다. inventory placement, floor, currency, media constraints, default timeout, auction policy가 여기에 속한다.

제품급 구조에서는 inventory 원본과 hot-path serving catalog를 분리한다. 현재 in-memory catalog는 fixture가 아니라 향후 외부 원본 저장소에서 로드될 serving copy의 최소 구현으로 본다.

| Aspect | Decision |
|---|---|
| Frequency | 요청보다 훨씬 낮은 빈도로 변경된다 |
| Lifetime | 운영 설정으로 장기 보존 대상이다 |
| Correctness | 잘못 적용되면 경매 결과와 수익 판단에 영향을 준다 |
| Hot path | 경매 시작과 BidRequest 생성에 필요하다 |
| Storage | 원본은 영속 저장, hot path는 serving copy가 적합하다 |

### 5.3 Demand-Side Campaign And Creative State

DSP가 입찰 여부와 응답을 판단하기 위해 사용하는 기준 데이터다. 캠페인 활성 여부, 지원 광고 타입, 타겟팅 조건, 입찰 정책, creative 참조 정보가 포함된다.

제품급 구조에서는 Campaign Data Store가 원본이고, DSP hot path는 Campaign Snapshot과 내부 index/repository를 읽는다. BidRequest 처리 중 Campaign Data Store를 동기 조회하지 않는 것을 기본 전제로 둔다.

| Aspect | Decision |
|---|---|
| Frequency | 운영 설정 데이터로 요청보다 낮은 빈도로 변경된다 |
| Lifetime | 재시작 후에도 복구되어야 한다 |
| Correctness | 잘못 적용되면 잘못된 bid/no-bid 또는 invalid bid가 발생한다 |
| Hot path | DSP bid decision과 BidResponse 생성에 필요하다 |
| Storage | 원본 저장소와 DSP serving snapshot/index 분리가 적합하다 |

### 5.4 Bid Decision State

DSP가 특정 BidRequest에 대해 bid 또는 no-bid를 결정하는 과정에서 생기는 파생 데이터다. 후보 캠페인, 매칭 결과, no-bid 사유, 계산된 입찰가, 선택된 creative 같은 중간 판단이 여기에 속한다.

| Aspect | Decision |
|---|---|
| Frequency | BidRequest와 같은 수준으로 매우 높다 |
| Lifetime | 대부분 요청 처리 중에만 필요하다 |
| Correctness | 현재 요청의 응답 생성에는 정확해야 한다 |
| Hot path | DSP hot path 내부 계산 결과다 |
| Storage | 기본은 저장하지 않는다. 디버깅/분석은 샘플링 또는 조건부 이벤트가 적합하다 |

### 5.5 Bid Response And Auction Result

DSP가 반환한 `BidResponse`와 SSP가 결정한 winner/no-winner 결과다. 입찰 가격, DSP별 결과 분류, 낙찰자, 낙찰가, invalid/timeout/late bid 판정이 여기에 속한다.

`BidResponse`는 외부 DSP가 반환한 observed fact이며 곧바로 winner 후보가 아니다. SSP의 Bid Judgment를 통과한 뒤에만 valid candidate가 된다. `AuctionResult`는 현재 프로젝트 응답이지만 제품급에서는 event output 또는 audit store 후보가 될 수 있다.

| Aspect | Decision |
|---|---|
| Frequency | 경매 요청마다 발생한다 |
| Lifetime | hot path 결과로 즉시 필요하며, 제품급에서는 사후 설명과 감사 목적의 보존 가치가 있다 |
| Correctness | 경매 결과이므로 요청 단위 판정은 정확해야 한다 |
| Hot path | SSP 낙찰 판단과 응답 생성에 직접 필요하다 |
| Storage | 현재는 메모리 처리와 응답 반환이 충분하다. 제품급에서는 append-only event 후보가 된다 |

### 5.6 Money, Transaction, Ledger

광고주 계좌, 캠페인 예산, reservation, 실제 차감, win/impression/billing event, 정산 원장 같은 데이터다.

현재 프로젝트 범위에는 포함하지 않는다. 다만 제품급 확장에서는 이 영역을 단순 캐시나 observability로 대체하면 안 된다. money state는 idempotency, 중복 차감 방지, 음수 잔액 방지, reconciliation, 감사 가능성을 요구한다.

| Aspect | Decision |
|---|---|
| Frequency | 입찰, 낙찰, 노출, 정산 이벤트에 따라 매우 자주 바뀔 수 있다 |
| Lifetime | 장기 보존과 복구가 필요하다 |
| Correctness | 가장 강한 정확성과 추적 가능성이 필요하다 |
| Hot path | 경매 제한 시간 안에 직접 넣을지는 별도 결정이 필요하다 |
| Storage | 원장, 영속 DB, append-only event, idempotency store를 별도 검토해야 한다 |

### 5.7 Real-Time Control, Analytics, Observability

pacing, frequency cap, rate limit, DSP health, 최근 win rate 같은 real-time control state는 빠른 보정과 bounded inconsistency가 중요한 경우가 많다. Analytics/reporting data는 원천 이벤트에서 만들어지는 집계 데이터다. Observability data는 metrics, logs, traces처럼 시스템 상태와 장애 원인을 설명하는 자료다.

이 셋은 서로 목적이 다르다. 특히 observability data는 비즈니스 원장이나 정산 기준 데이터의 대체물이 아니다.

| Data | Storage implication |
|---|---|
| Real-time control state | 인메모리 counter, Redis/Valkey, local cache, windowed aggregate 후보 |
| Analytics/reporting data | OLAP 저장소, batch/stream aggregate 후보 |
| Observability data | metrics backend, log store, trace backend |

## 6. Data Invariants

이 장의 불변조건은 필드 스키마가 아니라 데이터 의미와 저장/처리 책임에 대한 제약이다.

| Data | Invariant |
|---|---|
| `ProviderSlotRequest` | provider-facing 입력이며 OpenRTB 표준 객체가 아니다. 요청 자체는 장기 비즈니스 상태의 진실원이 아니라 경매 실행을 시작하는 transient input이다. |
| `InventoryPlacement` | provider/placement 조합은 SSP가 해석하는 공급 지면 기준 데이터다. 제품급 구조에서 원본은 외부 inventory store에 두고, hot path catalog는 그 원본에서 만든 serving copy로 본다. |
| SSP serving catalog | 입찰 제한 시간 안에서 provider/placement 조회를 제공해야 하며, 매 요청마다 외부 기준 저장소를 동기 조회하지 않는다. 장애 또는 재시작 후에는 inventory 원본에서 다시 구성 가능해야 한다. |
| Supply-side auction policy | 경매 방식, 통화, floor, 지원 매체 범위는 SSP가 BidRequest를 만들고 낙찰을 판단할 때 일관되게 적용되어야 한다. 현재 기본 정책은 first-price, single impression, USD, banner/simple video다. |
| OpenRTB `BidRequest` | SSP가 생성한 DSP-facing 파생 메시지다. `BidRequest.id`는 내부 `AuctionRequest.requestId`와 같아야 하고, 단일 `Imp`만 포함하며, `Imp.id`는 내부 `AuctionRequest.impId`와 같아야 한다. |
| `AuctionCommand` | Slot Ingress가 생성하는 첫 실행 가능 메시지다. 생성 이후 request id, impression id, media type, floor, currency, receivedAt의 의미가 auction execution 동안 바뀌면 안 된다. |
| DSP `CampaignSnapshot` | DSP hot path가 읽는 캠페인 상태는 기준 데이터의 serving copy다. 현재 구현에서는 시작 시점에 고정되며, BidRequest 처리 중 Campaign Data Store를 동기 조회하지 않는다. |
| DSP campaign index/repository | Campaign Snapshot에서 만든 파생 조회 구조다. index가 바뀌어도 같은 snapshot과 같은 요청에 대해 후보 캠페인의 의미가 달라지면 안 된다. |
| `DspCallResult` | DSP 호출 관찰 결과이며 유효 입찰 후보가 아니다. timeout, error, late bid, no-bid, bid-received 분류는 winner selection 전에 보존되어야 한다. |
| `BidResponse` | 외부 DSP가 반환한 observed fact이며, winner 후보가 되기 전 Bid Judgment를 통과해야 한다. request id, impression id, price, currency, media type, markup 조건이 원 요청과 맞지 않으면 유효 후보가 아니다. |
| `AuctionResult` | SSP가 산출한 프로젝트 응답이다. winner가 있으면 반드시 Bid Judgment를 통과한 후보에서 나와야 하고, no-winner는 정상 결과이며 시스템 오류로 취급하지 않는다. |
| Money state | 광고주 계좌, 예산, reservation, 차감 상태는 단순 캐시가 아니다. 제품급 구조에서는 중복 차감 방지, 음수 잔액 방지, 감사 가능성이 필요하다. |
| Transaction event | win, impression, billing, settlement 같은 비즈니스 사실은 제품급 구조에서 append-only event 후보로 본다. 기록된 사실은 수정보다 보정 이벤트로 처리하는 모델을 우선 검토한다. |
| Observability data | metrics, logs, traces는 시스템 진단 데이터이며 비즈니스 원장이나 정산 기준 데이터의 대체물이 아니다. |

### 6.1 Failure-Derived Invariants

이 불변조건은 Architecture의 실패 시나리오에서 도출한 것이다. 세부 구현 규칙이 아니라, 이후 상태 전이와 테스트로 내려갈 때 깨지면 안 되는 의미 규칙이다.

| Failure surface | Invariant |
|---|---|
| Request failure | 경매를 시작할 수 없는 요청은 DSP Gateway까지 전달되면 안 된다. |
| Request failure | `AcceptedSlotRequest`만 `AuctionCommand`를 가질 수 있다. |
| Timing failure | deadline 이후 도착한 bid는 가격과 무관하게 winner 후보가 될 수 없다. |
| DSP response failure | no-bid, timeout, late bid, invalid bid는 서로 다른 의미로 분류되어야 한다. |
| Competition | Winner Selector는 Bid Judgment를 통과한 valid candidate만 입력으로 받아야 한다. |
| Competition | 같은 입력과 같은 관찰 결과에 대해 winner decision은 재현 가능해야 한다. |
| No winner | valid candidate가 없으면 no-winner는 정상 경매 결과이며 시스템 장애가 아니다. |
| Serving state failure | Inventory serving catalog가 준비되지 않은 상태에서는 placement를 추측해서 경매를 시작하지 않는다. |
| Serving state failure | Campaign Snapshot이 준비되지 않은 DSP는 정상 bid 판단을 수행한 것으로 취급하지 않는다. |
| Duplicate / retry | 현재 hot path는 provider slot request retry에 대한 end-to-end idempotency를 보장하지 않는다. 이 보장은 event output 또는 money flow 도입 시 별도 설계 대상이다. |
| Event boundary | AuctionResult 이후의 business event가 필요해지면 event identity와 duplicate handling이 먼저 정의되어야 한다. |
| Observability | metrics/logs/traces의 존재 여부는 AuctionResult, billing, ledger의 진실성을 결정하지 않는다. |

## 7. Critical Invariants

다음 불변조건은 후속 설계에 큰 영향을 주며, 나중에 바꾸면 저장소 경계, 장애 복구, 테스트 전략, 성능 가정까지 다시 손봐야 하는 비용이 크다.

| Critical invariant | Why it is expensive to change |
|---|---|
| SSP inventory 원본과 hot-path serving catalog는 분리한다. | catalog를 원본으로 취급하면 persistence, migration, operator workflow, 장애 복구 모델이 모두 바뀐다. 반대로 원본/파생 분리를 전제로 하면 Redis/Valkey/local memory는 serving layer 후보로 비교할 수 있다. |
| DSP campaign 원본과 Campaign Snapshot/index는 분리한다. | BidRequest마다 campaign store를 동기 조회하는 구조로 바꾸면 latency budget, DSP scaling model, 장애 전파 범위가 바뀐다. snapshot을 원본으로 취급하면 캠페인 변경, 재시작 복구, 다중 DSP 일관성 모델이 흔들린다. |
| BidRequest와 AuctionRequest의 id/imp/media/floor/currency 정합성은 Slot Ingress에서 보장한다. | 이 정합성을 뒤 컴포넌트가 매번 추론하게 만들면 Auction Execution, Bid Judgment, Winner Decision이 모두 방어적이고 복잡해진다. |
| `AuctionCommand`는 불변 실행 컨텍스트다. | auction execution 중 요청 의미가 변할 수 있으면 deadline 계산, bid validation, result 설명 가능성, 재현 테스트가 모두 어려워진다. |
| DSP 호출 결과와 유효 bid candidate는 분리한다. | `DspCallResult`를 곧바로 후보로 보면 timeout, late, malformed, no-bid 분류가 winner selection에 섞이고 장애 분석과 판정 정확성이 낮아진다. |
| Winner는 반드시 Bid Judgment를 통과한 후보에서만 선택한다. | 이 조건이 깨지면 경매 정확성 자체가 흔들리고, 이후 과금/정산/event output을 신뢰할 수 없다. |
| Money state는 observability나 cache로 대체하지 않는다. | 예산/계좌를 캐시나 메트릭으로 처리하면 중복 차감, 음수 잔액, 정산 불일치가 발생한다. 나중에 원장 모델로 전환하는 비용이 매우 크다. |
| 비즈니스 transaction event와 metrics/logs는 분리한다. | observability 데이터를 과금/정산 근거로 사용하기 시작하면 retention, sampling, cardinality, privacy, 재처리 전략을 모두 바꿔야 한다. |
| 현재 hot path는 외부 저장소 동기 의존을 기본으로 두지 않는다. | 나중에 동기 DB/Redis 조회를 hot path에 넣으면 p95/p99, timeout 전파, backpressure, 장애 격리 전략이 바뀐다. 필요한 경우 별도 ADR과 성능 검증이 필요하다. |

## 8. State Transitions

이 장은 현재 hot path에서 필요한 상태 전이만 다룬다. money, ledger, billing, win notice, impression 같은 후속 흐름의 상태 전이는 아직 정의하지 않는다.

### 8.1 Auction Lifecycle

| From | Trigger | To | Meaning |
|---|---|---|---|
| Provider slot input | Slot Ingress accepts request | Auction command accepted | 경매 실행 가능한 내부 컨텍스트가 만들어졌다. |
| Provider slot input | Slot Ingress rejects request | Request rejected | DSP 호출 없이 요청 실패 결과로 끝난다. |
| Auction command accepted | Auction Execution starts DSP calls | Waiting for DSP results | deadline 안에서 DSP 응답을 수집한다. |
| Waiting for DSP results | Deadline reached or all calls observed | Judging bids | 수집된 DSP 호출 결과를 유효 후보와 비후보로 분류한다. |
| Judging bids | Valid candidate exists | Selecting winner | 경매 규칙으로 winner를 고른다. |
| Judging bids | No valid candidate exists | No winner result | 정상적인 no-winner 결과로 끝난다. |
| Selecting winner | Winner selected | Winner result | 유효 후보 중 하나가 낙찰 결과가 된다. |

### 8.2 DSP Result Classification

| Observed state | Transition rule | Candidate status |
|---|---|---|
| No response before deadline | classify as `TIMEOUT` | Not a candidate |
| Response after deadline | classify as `LATE_BID` | Not a candidate |
| Explicit no-bid | classify as `NO_BID` | Not a candidate |
| Transport or decode failure | classify as `ERROR` | Not a candidate |
| BidResponse received | send to Bid Judgment | Not a candidate yet |
| BidResponse fails validation | classify as `INVALID_BID` | Not a candidate |
| BidResponse passes validation | create valid candidate | Candidate |

### 8.3 Winner Decision

| Input | Transition | Result |
|---|---|---|
| Empty valid candidate list | skip winner selection | `NO_WINNER` |
| One valid candidate | select candidate | `WINNER` |
| Multiple valid candidates | apply first-price winner rule | `WINNER` |
| Multiple valid candidates with same price | apply deterministic tie-break | `WINNER` |

### 8.4 Serving State Lifecycle

| State | Transition | Meaning |
|---|---|---|
| Source data exists | load serving copy | SSP/DSP hot path can use local serving state. |
| Serving copy ready | handle auction request | hot path does not synchronously read source store. |
| Serving copy missing | reject or disable affected path | system must not guess placement or campaign state. |
| Process restarted | rebuild serving copy from source | derived serving state must be reconstructable. |
| Source data changed | refresh policy applies | freshness and cutover policy are future decisions. |

## 9. Research Boundary

저장소 기술 리서치는 다음 질문으로 좁힌다.

1. 어떤 데이터가 진실의 원본이어야 하는가?
2. 어떤 데이터가 hot path에서 인메모리 serving copy로 제공되어야 하는가?
3. 어떤 데이터가 append-only event로 남아야 장애 복구와 재처리가 가능한가?
4. 어떤 데이터는 전체 저장이 아니라 샘플링 또는 집계만으로 충분한가?
5. 어떤 데이터는 정확성이 최우선이고, 어떤 데이터는 bounded inconsistency를 허용할 수 있는가?

이 문서는 저장소 제품을 결정하지 않는다. PostgreSQL, Redis, Valkey, MemoryDB, Kafka, Kinesis, ClickHouse 같은 제품 비교는 데이터 소유권과 정확도 등급을 확정한 뒤 진행한다.

## 10. Decisions And Non-Decisions

확정:

- RTB 데이터는 단일 저장소 관점이 아니라 데이터 성질별로 분리해서 판단한다.
- 현재 프로젝트의 hot path는 Auction Opportunity, Campaign Snapshot, Bid Decision, Auction Result 중심이다.
- SSP inventory와 DSP campaign data는 메모리만을 진실의 원본으로 보지 않는다. 제품급 구조에서는 외부 기준 저장소가 원본이고, hot path는 그 데이터를 로드한 in-memory serving copy를 읽는다.
- Money State, Transaction Event, Ledger, Analytics는 현재 구현 범위 밖이지만 제품급 확장 논의에서는 별도 데이터 종류로 다룬다.
- Observability 데이터는 시스템 진단 자료이며 비즈니스 원장으로 사용하지 않는다.
- Slot Ingress에서 생성된 `AuctionCommand`는 auction execution 동안 의미가 바뀌지 않는 실행 컨텍스트로 본다.

아직 결정하지 않음:

- SSP store와 DSP store의 구체적인 소유 데이터.
- SSP inventory와 DSP campaign 기준 저장소의 제품 선택.
- in-memory serving copy를 프로세스 내부 메모리, Redis/Valkey 계열, 또는 managed memory store 중 어디에 둘지.
- budget reservation을 bid 시점에 수행할지, win 시점에만 차감할지.
- event output을 현재 코드에 port로 둘지, 문서상의 future boundary로만 둘지.
- 분석/리포팅 저장소의 도입 여부.
