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

## 6. Decisions And Non-Decisions

확정:

- RTB 데이터는 단일 저장소 관점이 아니라 데이터 성질별로 분리해서 판단한다.
- 현재 프로젝트의 hot path는 Auction Opportunity, Campaign Snapshot, Bid Decision, Auction Result 중심이다.
- SSP inventory와 DSP campaign data는 메모리만을 진실의 원본으로 보지 않는다. 제품급 구조에서는 외부 기준 저장소가 원본이고, hot path는 그 데이터를 로드한 in-memory serving copy를 읽는다.
- Money State, Transaction Event, Ledger, Analytics는 현재 구현 범위 밖이지만 제품급 확장 논의에서는 별도 데이터 종류로 다룬다.
- Observability 데이터는 시스템 진단 자료이며 비즈니스 원장으로 사용하지 않는다.

아직 결정하지 않음:

- SSP store와 DSP store의 구체적인 소유 데이터.
- SSP inventory와 DSP campaign 기준 저장소의 제품 선택.
- in-memory serving copy를 프로세스 내부 메모리, Redis/Valkey 계열, 또는 managed memory store 중 어디에 둘지.
- budget reservation을 bid 시점에 수행할지, win 시점에만 차감할지.
- event output을 현재 코드에 port로 둘지, 문서상의 future boundary로만 둘지.
- 분석/리포팅 저장소의 도입 여부.
