# RTB Data Architecture

이 문서는 RTB 경매 데이터가 시스템 안에서 어떤 상태로 존재하는지 정의한다. DB 스키마나 제품 선택보다 먼저 source of truth, serving copy, transient state, business event를 구분한다.

API 필드는 `api-interface-specification.md`, runtime 구조는 `architecture-description.md`, 구현 컴포넌트는 `implementation-technical-specification.md`를 기준으로 한다.

## 1. Data Categories

| Category | Meaning | Storage implication |
|---|---|---|
| Source of truth | 시스템이 기준으로 삼는 원본 상태 | 영속 저장, 변경 이력, 운영 관리 |
| Serving copy | source of truth에서 만든 hot path 조회 상태 | process memory, Redis/Valkey, materialized view 후보 |
| Transient input | 요청 처리 중 들어왔다가 사라지는 입력 | 메모리 처리, 필요 시 샘플링 |
| Transient decision | 요청 처리 중 계산되는 중간 판단 | 메모리 처리, 필요 시 debug log |
| External observation | DSP 응답처럼 외부 호출에서 관찰한 결과 | 검증 전 관찰값 |
| Business event | 시간이 지나도 의미가 있는 비즈니스 사실 | append-only log, outbox, stream 후보 |
| Ledger state | 돈, 차감, 정산 기준 상태 | 강한 정합성, idempotency, reconciliation |
| Observability data | 시스템 상태와 원인 분석 자료 | metrics, logs, traces |

`External observation`은 곧바로 비즈니스 진실이 아니다. 예를 들어 BidResponse는 관찰값이고, Bid Judgment를 통과해야 winner candidate가 된다.

## 2. Project Data Boundary

현재 hot path에서 직접 다루는 데이터:

| Data | Category | Meaning |
|---|---|---|
| `ProviderSlotRequest` | Transient input | 광고 슬롯 요청을 표현하는 provider-facing 입력 |
| `InventoryPlacement` | Serving copy | SSP가 provider/placement를 해석하기 위해 읽는 공급 지면 상태 |
| OpenRTB `BidRequest` | Derived message | SSP가 DSP에 보내는 입찰 요청 |
| `AuctionCommand` | Transient decision context | 경매 실행에 필요한 불변 컨텍스트 |
| `CampaignSnapshot` | Serving copy | DSP가 bid/no-bid 판단에 사용하는 캠페인 상태 |
| `DspCallResult` | External observation | DSP 호출 결과 관찰값 |
| `BidResponse` | External observation | DSP가 반환한 OpenRTB 응답 |
| `AuctionResult` | Result message | SSP가 provider-facing 경로로 반환하는 경매 결과 |
| Metrics/logs/traces | Observability data | 성능과 장애 원인 설명 자료 |

현재 hot path에서 직접 다루지 않는 데이터:

| Data | Reason |
|---|---|
| Money / budget / ledger | strong consistency와 reconciliation 설계가 먼저 필요하다. |
| Win/impression/billing event | event identity와 duplicate handling이 먼저 필요하다. |
| Analytics/reporting | hot path 결과에서 파생되는 별도 제품 영역이다. |
| Real-time control state | pacing, frequency cap, routing은 현재 범위 밖이다. |

## 3. Source Of Truth And Serving Copy

핵심 원칙은 원본과 hot path 조회 상태를 분리하는 것이다.

| Domain | Source of truth candidate | Serving copy |
|---|---|---|
| Supply-side inventory | Inventory Store | Inventory Catalog |
| Demand-side campaign | Campaign Data Store | Campaign Snapshot / Index |

Serving copy는 빠른 조회를 위한 상태이지 원본이 아니다. 재시작이나 장애 후에는 source of truth에서 다시 만들 수 있어야 한다.

현재 재현 가능한 initial data나 in-memory catalog는 임시 가짜 데이터로 취급하지 않는다. 향후 외부 source of truth에서 로드될 serving copy의 최소 구현으로 본다.

## 4. Data Constraints

데이터 제약조건은 저장소 제품 선택보다 먼저 지켜야 하는 조건이다. 이 조건을 어기면 PostgreSQL, Redis/Valkey, stream, in-process memory 중 무엇을 선택하든 잘못된 모델이 된다.

| Constraint | Architectural consequence |
|---|---|
| Source of truth와 serving copy는 같은 책임이 아니다. | Redis/Valkey나 process memory를 쓰더라도 그것이 운영 원본인지 hot path 복제본인지 먼저 구분해야 한다. |
| Serving copy는 재구성 가능해야 한다. | process restart나 cache loss 이후 원본에서 다시 만들 수 없는 상태는 serving copy로 둘 수 없다. |
| Hot path transient state는 장기 진실원이 아니다. | `AuctionCommand`, Bid Judgment result, Winner Decision은 요청 단위 정합성은 가져야 하지만 장기 저장 기준이 아니다. |
| External observation은 검증 전 사실이다. | DSP가 BidResponse를 반환했다는 사실과 그 bid가 유효하다는 판단은 분리되어야 한다. |
| Business event가 도입되면 identity가 먼저 필요하다. | win/impression/billing event는 duplicate handling 없이 append-only stream에 넣으면 안 된다. |
| Ledger state는 cache나 metrics로 대체할 수 없다. | budget, charge, settlement는 idempotency와 reconciliation을 전제로 별도 모델링해야 한다. |
| Observability data는 샘플링과 누락을 허용할 수 있다. | metrics/logs/traces를 billing, ledger, audit source로 사용하면 안 된다. |
| SSP와 DSP store ownership은 분리해서 생각한다. | 같은 DB 제품을 쓰더라도 SSP inventory와 DSP campaign은 서로 다른 소유 데이터이며 장애/변경 경계도 다르다. |

## 5. Core Invariants

| Invariant | Why it matters |
|---|---|
| 경매를 시작할 수 없는 요청은 DSP Gateway까지 전달되지 않는다. | 불필요한 외부 호출과 모호한 실패를 막는다. |
| `AuctionCommand`는 생성 이후 경매 종료까지 의미가 바뀌지 않는다. | deadline, validation, result explanation이 안정된다. |
| OpenRTB BidRequest와 내부 AuctionRequest의 id/imp/media/floor/currency는 일치해야 한다. | 뒤 컴포넌트가 요청 의미를 다시 추론하지 않게 한다. |
| `DspCallResult`와 valid bid candidate는 분리한다. | timeout, no-bid, invalid bid가 winner selection에 섞이지 않는다. |
| Winner는 반드시 Bid Judgment를 통과한 candidate에서만 나온다. | 경매 정확성과 이후 event/ledger 신뢰성을 지킨다. |
| `NO_WINNER`는 정상 경매 결과일 수 있다. | 장애와 비낙찰을 구분한다. |
| Observability data는 business event나 ledger의 대체물이 아니다. | metrics 누락이나 샘플링이 비즈니스 진실을 바꾸면 안 된다. |

## 6. State Transitions

### Auction Lifecycle

| From | To | Rule |
|---|---|---|
| Provider slot input | Request rejected | Slot Ingress가 요청을 경매 불가로 판단한다. |
| Provider slot input | Auction command accepted | 요청과 inventory가 경매 실행 가능 상태로 정규화된다. |
| Auction command accepted | Waiting for DSP results | Auction Flow가 deadline을 정하고 DSP 호출을 시작한다. |
| Waiting for DSP results | Judging bids | deadline 도달 또는 모든 결과 관찰 후 검증한다. |
| Judging bids | Winner result | valid candidate가 있고 winner rule이 적용된다. |
| Judging bids | No-winner result | valid candidate가 없다. |

### DSP Result Classification

| Observation | Classification | Candidate? |
|---|---|---|
| No response before deadline | `TIMEOUT` | No |
| Response after deadline | `LATE_BID` | No |
| Explicit no-bid | `NO_BID` | No |
| Transport or decode failure | `ERROR` | No |
| BidResponse fails validation | `INVALID_BID` | No |
| BidResponse passes validation | valid candidate | Yes |

## 7. Consistency Requirements

| Consistency class | Data |
|---|---|
| Request-local consistency | `AuctionCommand`, Bid Judgment result, Winner Decision |
| Rebuildable serving consistency | Inventory Catalog, Campaign Snapshot / Index |
| Append-only fact consistency | future win/impression/billing events |
| Strong business consistency | future budget, ledger, settlement |
| Best-effort diagnostic consistency | metrics, logs, traces |

현재 hot path는 request-local consistency가 핵심이다. money/ledger는 strong business consistency가 필요하므로 캐시나 metrics로 대체하지 않는다.

## 8. Deferred Data Decisions

| Decision | Why deferred |
|---|---|
| Inventory/Campaign source DB product | 먼저 source of truth와 serving copy ownership을 확정해야 한다. |
| Redis/Valkey 사용 여부 | serving copy freshness, rebuild, failure model이 먼저다. |
| Event output | event identity, duplicate handling, replay policy가 필요하다. |
| Budget reservation timing | latency와 overspend risk 사이 trade-off가 크다. |
| Retention/deletion policy | business event와 observability data의 목적이 다르다. |
