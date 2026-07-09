# RTB Data State Architecture

## 1. Purpose

이 문서는 RTB 경매와 입찰에서 등장하는 데이터의 정체를 분류하고, 어떤 데이터가 진실원(source of truth), 파생 상태(derived state), 일시 입력(transient input), 이벤트(event), 관측 데이터(observability data)인지 판단하기 위한 기준을 세운다.

목적은 필드나 스키마를 먼저 정의하는 것이 아니라, 각 데이터가 어떤 비즈니스 의미를 가지며 어떤 저장/처리 특성을 요구하는지 판단하기 위한 기준을 세우는 것이다. 저장소 제품 선택, SSP/DSP store ownership, budget reservation, event output, 장애 복구, 정합성 모델 논의는 이 분류를 바탕으로 별도 결정한다.

이 문서는 현재 프로젝트의 구현 범위를 확장하지 않는다. 현재 구현은 Provider Slot Request를 받아 SSP가 OpenRTB BidRequest를 생성하고, BidResponse 수집과 낙찰 판단까지 이어지는 hot path에 집중한다. 과금, 정산, 리포팅, 광고 운영 백오피스는 여전히 범위 밖이다.

## 2. Classification Criteria

각 데이터는 다음 기준으로 분류한다.

| 기준 | 설명 |
|---|---|
| Business meaning | 데이터가 비즈니스에서 무엇을 의미하는가 |
| Frequency | 경매 요청 단위로 초고빈도 발생하는가, 운영 설정처럼 저빈도 변경되는가 |
| Lifetime | 요청 처리 중에만 필요한가, 장기 보존해야 하는가 |
| Correctness | 강한 정확성이 필요한가, 짧은 불일치나 근사치가 허용되는가 |
| Hot path usage | 입찰 제한 시간 안의 동기 처리 경로에서 필요한가 |
| Storage implication | 영속 저장, 인메모리 serving copy, event log, 샘플링 중 어떤 접근이 자연스러운가 |

## 3. Data Types

### 3.1 Auction Opportunity Data

광고 노출 기회와 경매 입력을 표현하는 데이터다. 현재 provider-facing 경로에서는 `ProviderSlotRequest`, SSP inventory placement, SSP가 생성한 OpenRTB `BidRequest`가 함께 이 성격을 이룬다.

`ProviderSlotRequest`는 광고 슬롯이 열렸다는 사실과 provider/placement, 광고 타입, 슬롯 제약을 표현한다. SSP inventory placement는 그 슬롯이 실제로 어떤 공급 지면인지, 어떤 크기/동영상 조건/floor/default timeout을 갖는지 정의한다. 생성된 `BidRequest`는 DSP에게 전달되는 표준 입찰 요청이다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 매우 높음. 경매 요청마다 새로 생성된다 |
| Lifetime | 짧음. 대부분 경매 처리 중에만 필요하다 |
| Correctness | 요청 검증과 inventory 매칭에는 정확성이 필요하지만 개별 요청 원본 자체가 장기 상태의 진실은 아니다 |
| Hot path usage | SSP와 DSP 양쪽 hot path에서 직접 사용된다 |
| Storage implication | 기본은 메모리 객체로 처리한다. 사후 분석은 샘플링 또는 축약 이벤트가 적합하다 |

현재 프로젝트에서는 `ProviderSlotRequest`를 동기 처리 입력으로 사용하고, SSP가 생성한 `BidRequest`를 메모리에서 처리한다. 개별 요청 원본과 생성된 BidRequest를 영속 저장하지 않는다.

provider slot request와 inventory 설정은 성격이 다르다. slot request는 경매마다 새로 생기는 고빈도 이벤트성 입력이고, inventory 설정은 낮은 빈도로 바뀌는 공급 측 기준 데이터다. 따라서 제품급 확장에서는 inventory 원본은 영속 저장하고 hot path에는 인메모리 serving copy를 두는 쪽이 자연스럽다.

### 3.2 Supply-Side Auction Policy Data

SSP 또는 exchange가 경매를 어떻게 열고 판정할지 결정하는 데이터다. 경매 방식, floor policy, publisher policy, deal policy, DSP 참여 설정 같은 공급 측 정책이 여기에 속한다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 요청보다 훨씬 낮은 빈도로 변경된다 |
| Lifetime | 운영 설정으로 장기 보존 대상이다 |
| Correctness | 잘못 적용되면 경매 결과와 수익 배분에 영향을 준다 |
| Hot path usage | 경매 시작과 낙찰 판정에 필요하다 |
| Storage implication | 원본은 영속 저장, hot path에서는 캐시 또는 snapshot이 적합하다 |

현재 프로젝트에서는 first-price auction, 단일 impression, USD 통화 같은 정책을 코드와 문서의 고정 규칙으로 둔다.

### 3.3 Demand-Side Campaign Configuration Data

DSP가 입찰 여부를 판단하기 위해 사용하는 광고주 캠페인 설정 데이터다. 캠페인 활성 여부, 지원 광고 타입, 타겟팅 조건, 입찰 정책, creative 참조 정보가 여기에 속한다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 운영 설정 데이터로, BidRequest보다 훨씬 낮은 빈도로 변경된다 |
| Lifetime | 재시작 후에도 복구되어야 하는 기준 데이터다 |
| Correctness | 잘못 적용되면 잘못된 bid/no-bid가 발생한다 |
| Hot path usage | DSP bid decision에서 매우 자주 읽힌다 |
| Storage implication | 원본은 영속 저장, DSP serving 경로는 인메모리 snapshot/index가 적합하다 |

현재 프로젝트에서는 부팅 시 구성한 `CampaignSnapshot`을 DSP 메모리에 올리고, BidRequest 처리 중 외부 저장소를 동기 조회하지 않는다.

### 3.4 Creative Eligibility And Response Data

입찰 응답을 만들 수 있는 광고 소재의 최소 참조 데이터다. creative id, advertiser domain, 테스트용 adm, 광고 타입 적합성 같은 정보가 여기에 속한다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 설정 변경 빈도는 낮지만 입찰 판단 중 자주 읽힌다 |
| Lifetime | 운영 기준 데이터로 장기 보존 대상이다 |
| Correctness | 잘못된 creative는 invalid bid, 심사 위반, 렌더링 실패로 이어질 수 있다 |
| Hot path usage | BidResponse 생성과 검증에 필요하다 |
| Storage implication | 원본 저장소와 DSP 인메모리 snapshot을 분리하는 접근이 적합하다 |

현재 프로젝트에서는 실제 광고 소재 저장, CDN 배포, 광고 심사 상태를 다루지 않고, BidResponse 생성에 필요한 최소 참조만 다룬다.

### 3.5 Bid Decision Data

DSP가 특정 BidRequest에 대해 bid 또는 no-bid를 결정하는 과정에서 생기는 파생 데이터다. 후보 캠페인, 매칭 결과, no-bid 사유, 계산된 입찰가, 선택된 creative 같은 중간 판단이 여기에 속한다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | BidRequest와 같은 수준으로 매우 높다 |
| Lifetime | 대부분 요청 처리 중에만 필요하다 |
| Correctness | 현재 요청의 응답 생성에는 정확해야 하지만, 전체 보존은 비용이 크다 |
| Hot path usage | DSP hot path 내부 계산 결과다 |
| Storage implication | 기본은 저장하지 않는다. 디버깅, 감사, 모델 분석 목적은 샘플링 또는 조건부 이벤트가 적합하다 |

현재 프로젝트에서는 bid/no-bid 결과와 일부 사유를 메트릭과 응답 결과로 관찰하지만, 모든 결정 과정을 영속 저장하지 않는다.

### 3.6 Bid Response And Auction Result Data

DSP가 반환한 `BidResponse`와 SSP가 결정한 winner/no-winner 결과다. 입찰 가격, DSP별 결과 분류, 낙찰자, 낙찰가, invalid/timeout/late bid 판정이 여기에 속한다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 경매 요청마다 발생한다 |
| Lifetime | hot path 결과로 즉시 필요하며, 제품급에서는 사후 설명과 감사 목적으로 일부 보존이 필요할 수 있다 |
| Correctness | 경매 결과이므로 요청 단위 판정은 정확해야 한다 |
| Hot path usage | SSP의 낙찰 판단과 응답 생성에 직접 필요하다 |
| Storage implication | 현재는 메모리 처리와 응답 반환이 충분하다. 제품급에서는 append-only event output 또는 audit store가 필요할 수 있다 |

현재 프로젝트에서는 `AuctionResult`를 테스트 클라이언트에 반환하고, 전체 결과 이벤트 저장소는 두지 않는다.

### 3.7 Money State Data

광고주 계좌, 캠페인 예산, 사용 가능 잔액, 예약 금액, 실제 차감 금액처럼 돈의 현재 상태를 표현하는 데이터다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 입찰, 낙찰, 노출, 정산 이벤트에 따라 매우 자주 바뀔 수 있다 |
| Lifetime | 장기 보존과 복구가 필요하다 |
| Correctness | 매우 강한 정확성과 감사 가능성이 필요하다 |
| Hot path usage | 제품급 DSP에서는 bid 전 예산 확인 또는 reservation에 필요할 수 있다 |
| Storage implication | 영속 원장과 인메모리 reservation view를 분리해서 고려해야 한다 |

이 데이터는 현재 프로젝트 범위에 포함하지 않는다. 다만 제품급 확장을 고려하면 단순 캐시 데이터가 아니라 ledger, idempotency, reservation, reconciliation의 대상이다.

### 3.8 Real-Time Control State Data

pacing, frequency cap, rate limit, DSP health, 최근 win rate, QPS 제어처럼 실시간 의사결정을 조정하는 상태 데이터다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 매우 자주 갱신될 수 있다 |
| Lifetime | 짧거나 중간 정도다. 일부는 window가 지나면 의미가 사라진다 |
| Correctness | 완전한 정확성보다 bounded inconsistency와 빠른 보정이 중요한 경우가 많다 |
| Hot path usage | bid/no-bid, 가격 조정, traffic shaping에 쓰일 수 있다 |
| Storage implication | 인메모리 counter, local cache, Redis 계열 저장소, windowed aggregate가 후보가 된다 |

현재 프로젝트에서는 pacing, frequency cap, routing optimization을 다루지 않는다.

### 3.9 Transaction Event Data

입찰 제출, 낙찰 통지, 패배 통지, impression, click, conversion, billing event처럼 시간이 지나며 발생하는 비즈니스 사실을 append-only로 표현하는 데이터다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 매우 높을 수 있다 |
| Lifetime | 재처리, 정산, 리포팅, 장애 복구를 위해 보존 가치가 높다 |
| Correctness | 중복 처리 방지를 위한 idempotency가 중요하다 |
| Hot path usage | 경매 응답 자체에는 동기 의존시키지 않는 것이 일반적이다 |
| Storage implication | append-only stream, outbox, event log가 자연스럽다 |

현재 프로젝트에서는 win notice, billing notice, impression/click/conversion을 범위 밖으로 둔다. 제품급 논의에서는 SSP/DSP store를 직접 공유하지 않고 이벤트로 동기화하는 경계가 필요하다.

### 3.10 Ledger And Settlement Data

실제 청구, 차감, 환불, 수익 배분, 광고주/매체사 정산의 기준이 되는 데이터다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 이벤트 기반으로 계속 증가하지만 hot path보다 후행 처리에 가깝다 |
| Lifetime | 장기 보존, 감사, 재계산 대상이다 |
| Correctness | 가장 강한 정확성과 추적 가능성이 필요하다 |
| Hot path usage | 경매 제한 시간 안의 동기 경로에 직접 넣지 않는 것이 적합하다 |
| Storage implication | 영속 DB, ledger, event-sourced store, warehouse 적재를 검토해야 한다 |

현재 프로젝트에서는 과금과 정산을 구현하지 않는다.

### 3.11 Analytics And Reporting Data

캠페인 성과, spend, win rate, eCPM, fill rate, publisher revenue 같은 집계 데이터다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 원천 이벤트는 고빈도이고, 조회는 집계 중심이다 |
| Lifetime | 장기 추세 분석과 운영 의사결정에 필요하다 |
| Correctness | 실시간 근사치와 정산용 확정 집계를 구분해야 한다 |
| Hot path usage | 경매 응답 경로에 직접 필요하지 않다 |
| Storage implication | OLAP 저장소나 batch/stream 집계가 적합하다 |

현재 프로젝트에서는 리포팅 시스템을 구현하지 않는다.

### 3.12 Observability Data

metrics, logs, traces처럼 시스템 상태와 장애 원인을 설명하는 데이터다.

특징:

| 기준 | 성질 |
|---|---|
| Frequency | 매우 높을 수 있다 |
| Lifetime | 운영 분석 기간만큼 보존한다 |
| Correctness | 비즈니스 원장이 아니라 시스템 진단 자료다 |
| Hot path usage | hot path에서 생성되지만, 동기 처리 비용을 낮춰야 한다 |
| Storage implication | metrics backend, log store, trace backend로 분리한다 |

현재 프로젝트에서는 Micrometer metrics와 OpenTelemetry 기반 관찰 가능성을 다룬다. Observability 데이터는 정산이나 원장 데이터의 대체물이 아니다.

## 4. Current Project Boundary

현재 구현에서 직접 다루는 데이터는 다음이다.

| 데이터 | 현재 처리 |
|---|---|
| Auction Opportunity Data | `ProviderSlotRequest`로 수신하고 SSP가 OpenRTB `BidRequest`를 생성해 메모리에서 처리 |
| Supply-Side Auction Policy Data | first-price, single-imp, USD 같은 고정 규칙과 in-memory inventory serving copy로 처리 |
| Demand-Side Campaign Configuration Data | DSP 시작 시 sample `CampaignSnapshot`으로 구성 |
| Creative Eligibility And Response Data | BidResponse 생성을 위한 최소 creative 참조만 사용 |
| Bid Decision Data | DSP 내부 계산으로만 사용하고 전체 과정은 저장하지 않음 |
| Bid Response And Auction Result Data | SSP가 수집/검증/판정 후 `AuctionResult`로 반환 |
| Observability Data | metrics/logs/traces로 시스템 상태를 관찰 |

현재 구현에서 직접 다루지 않는 데이터는 다음이다.

| 데이터 | 제외 이유 |
|---|---|
| Money State Data | 계좌, 예산, reservation, 차감은 제품급 money flow 설계가 먼저 필요함 |
| Real-Time Control State Data | pacing, frequency cap, dynamic routing은 현재 hot path 검증 범위 밖임 |
| Transaction Event Data | win/impression/billing 후속 흐름이 현재 범위 밖임 |
| Ledger And Settlement Data | 과금/정산 시스템은 현재 목표가 아님 |
| Analytics And Reporting Data | 리포팅 저장소와 집계 파이프라인은 별도 제품 영역임 |

## 5. Research Boundary

저장소 기술 리서치는 다음 질문으로 좁힌다.

1. 어떤 데이터가 진실의 원본이어야 하는가?
2. 어떤 데이터가 hot path에서 인메모리 serving copy로 제공되어야 하는가?
3. 어떤 데이터가 append-only event로 남아야 장애 복구와 재처리가 가능한가?
4. 어떤 데이터는 전체 저장이 아니라 샘플링 또는 집계만으로 충분한가?
5. 어떤 데이터는 정확성이 최우선이고, 어떤 데이터는 bounded inconsistency를 허용할 수 있는가?

이 문서는 저장소 제품을 결정하지 않는다. PostgreSQL, Redis, Valkey, MemoryDB, Kafka, Kinesis, ClickHouse 같은 제품 비교는 데이터 소유권과 정확도 등급을 확정한 뒤 진행한다.

## 6. Data Invariants

이 장의 불변조건은 필드 스키마가 아니라 데이터 의미와 저장/처리 책임에 대한 제약이다. 이후 DB, Redis/Valkey, event log, local memory 선택은 이 불변조건을 깨지 않는 방향으로 검토한다.

| 데이터 | 불변조건 |
|---|---|
| `ProviderSlotRequest` | provider-facing 입력이며 OpenRTB 표준 객체가 아니다. 요청 자체는 장기 비즈니스 상태의 진실원이 아니라 경매 실행을 시작하는 transient input이다. |
| `InventoryPlacement` | provider/placement 조합은 SSP가 해석하는 공급 지면 기준 데이터다. 제품급 구조에서 원본은 외부 inventory store에 두고, hot path의 catalog는 그 원본에서 만든 serving copy로 본다. |
| SSP serving catalog | 입찰 제한 시간 안에서 provider/placement 조회를 제공해야 하며, 매 요청마다 외부 기준 저장소를 동기 조회하지 않는다. catalog 장애 또는 재시작 후에는 inventory 원본에서 다시 구성 가능해야 한다. |
| Supply-side auction policy | 경매 방식, 통화, floor, 지원 매체 범위는 SSP가 BidRequest를 만들고 낙찰을 판단할 때 일관되게 적용되어야 한다. 현재 프로젝트의 기본 정책은 first-price, single impression, USD, banner/simple video다. |
| OpenRTB `BidRequest` | SSP가 생성한 DSP-facing 파생 메시지다. `BidRequest.id`는 내부 `AuctionRequest.requestId`와 같아야 하고, 단일 `Imp`만 포함하며, `Imp.id`는 내부 `AuctionRequest.impId`와 같아야 한다. |
| `AuctionCommand` | Slot Ingress가 생성하는 첫 실행 가능 메시지다. 이 메시지가 생성된 뒤에는 request id, impression id, media type, floor, currency, receivedAt의 의미가 auction execution 동안 바뀌면 안 된다. |
| DSP `CampaignSnapshot` | DSP hot path가 읽는 캠페인 상태는 기준 데이터의 serving copy다. 현재 구현에서는 시작 시점에 고정되며, BidRequest 처리 중 Campaign Data Store를 동기 조회하지 않는다. |
| DSP campaign index/repository | Campaign Snapshot에서 만든 파생 조회 구조다. index가 바뀌어도 같은 snapshot과 같은 요청에 대해 후보 캠페인의 의미가 달라지면 안 된다. |
| `BidResponse` | 외부 DSP가 반환한 observed fact이며, winner 후보가 되기 전 Bid Judgment를 통과해야 한다. request id, impression id, price, currency, media type, markup 조건이 원 요청과 맞지 않으면 유효 후보가 아니다. |
| `DspCallResult` | DSP 호출 관찰 결과이며 유효 입찰 후보가 아니다. timeout, error, late bid, no-bid, bid-received 분류는 winner selection 전에 보존되어야 한다. |
| `AuctionResult` | SSP가 산출한 프로젝트 응답이다. winner가 있으면 반드시 Bid Judgment를 통과한 후보에서 나와야 하고, no-winner는 정상 결과이며 시스템 오류로 취급하지 않는다. |
| Money state | 광고주 계좌, 예산, reservation, 차감 상태는 단순 캐시가 아니다. 제품급 구조에서는 중복 차감 방지, 음수 잔액 방지, 감사 가능성이 필요하다. |
| Transaction event | win, impression, billing, settlement 같은 비즈니스 사실은 제품급 구조에서 append-only event 후보로 본다. 기록된 사실을 수정하기보다 보정 이벤트로 처리하는 모델을 우선 검토한다. |
| Observability data | metrics, logs, traces는 시스템 진단 데이터이며 비즈니스 원장이나 정산 기준 데이터의 대체물이 아니다. |

## 7. Critical Invariants

다음 불변조건은 후속 설계에 큰 영향을 주며, 나중에 바꾸면 저장소 경계, 장애 복구, 테스트 전략, 성능 가정까지 다시 손봐야 하는 비용이 크다.

| Critical invariant | Why it is expensive to change |
|---|---|
| SSP inventory 원본과 hot-path serving catalog는 분리한다. | catalog를 원본으로 취급하면 persistence, migration, operator workflow, 장애 복구 모델이 모두 바뀐다. 반대로 원본/파생 분리를 전제로 하면 Redis/Valkey/local memory는 serving layer 후보로 비교할 수 있다. |
| DSP campaign 원본과 Campaign Snapshot/index는 분리한다. | BidRequest마다 campaign store를 동기 조회하는 구조로 바꾸면 latency budget, DSP scaling model, 장애 전파 범위가 바뀐다. snapshot을 원본으로 취급하면 캠페인 변경, 재시작 복구, 다중 DSP 일관성 모델이 흔들린다. |
| BidRequest와 AuctionRequest의 id/imp/media/floor/currency 정합성은 Slot Ingress에서 보장한다. | 이 정합성을 뒤 컴포넌트가 매번 추론하게 만들면 Auction Execution, Bid Judgment, Winner Decision이 모두 방어적이고 복잡해진다. |
| AuctionCommand는 불변 실행 컨텍스트다. | auction execution 중 요청 의미가 변할 수 있으면 deadline 계산, bid validation, result 설명 가능성, 재현 테스트가 모두 어려워진다. |
| DSP 호출 결과와 유효 bid candidate는 분리한다. | DspCallResult를 곧바로 후보로 보면 timeout, late, malformed, no-bid 분류가 winner selection에 섞이고 장애 분석과 판정 정확성이 낮아진다. |
| Winner는 반드시 Bid Judgment를 통과한 후보에서만 선택한다. | 이 조건이 깨지면 경매 정확성 자체가 흔들리고, 이후 과금/정산/event output을 신뢰할 수 없다. |
| Money state는 observability나 cache로 대체하지 않는다. | 예산/계좌를 캐시나 메트릭으로 처리하면 중복 차감, 음수 잔액, 정산 불일치가 발생한다. 나중에 원장 모델로 전환하는 비용이 매우 크다. |
| 비즈니스 transaction event와 metrics/logs는 분리한다. | observability 데이터를 과금/정산의 근거로 사용하기 시작하면 retention, sampling, cardinality, privacy, 재처리 전략을 모두 바꿔야 한다. |
| 현재 hot path는 외부 저장소 동기 의존을 기본으로 두지 않는다. | 나중에 동기 DB/Redis 조회를 hot path에 넣으면 p95/p99, timeout 전파, backpressure, 장애 격리 전략이 바뀐다. 필요한 경우 별도 ADR과 성능 검증이 필요하다. |

## 8. Decisions And Non-Decisions

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
