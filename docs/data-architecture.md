# RTB Data Architecture

이 문서는 현재 경매 hot path에서 다루는 데이터의 성격과 경계를 정의한다. 특정 코드 객체, DB 제품, Redis/Valkey, event stream, ledger 설계는 이 문서의 현재 범위가 아니다.

API 필드는 `api-interface-specification.md`, runtime 구조는 `architecture-description.md`, 구현 컴포넌트는 `implementation-technical-specification.md`를 기준으로 한다.

## 1. Data Problem

RTB 경매 hot path에서 중요한 데이터 문제는 "무엇이 들어왔는가"보다 "어떤 데이터가 어떤 판단 단계에서 어떤 의미를 갖는가"다.

DSP가 입찰 응답을 반환했다는 사실만으로 그 bid가 winner 후보가 되는 것은 아니다. SSP는 원 요청, deadline, media constraint, bidfloor, currency, DSP 응답 상태를 기준으로 외부 관찰값을 검증하고, 유효한 bid 후보만 낙찰 판단으로 넘겨야 한다.

따라서 이 문서의 핵심 관심사는 다음이다.

- provider-facing 입력과 DSP-facing 입찰 요청을 구분한다.
- 외부에서 관찰한 DSP 결과와 내부적으로 인정한 유효 bid 후보를 구분한다.
- 요청 단위 판단 결과와 장기 저장해야 하는 비즈니스 사실을 구분한다.
- metrics/logs/traces가 경매 결과의 원본을 대체하지 않도록 한다.

## 2. Data Categories

| Category | Meaning | Current role |
|---|---|---|
| Input data | 요청 처리의 시작점이 되는 외부 입력 | 광고 슬롯이 열렸다는 provider-facing 요청 |
| Serving data | hot path 판단을 위해 빠르게 읽는 기준 데이터 | 광고 지면, 캠페인 판단 기준 |
| Derived message | 시스템이 다른 경계로 보내기 위해 만든 메시지 | DSP에게 전달할 입찰 요청 |
| Decision context | 요청 처리 중 판단을 안정시키는 내부 문맥 | 경매 실행에 필요한 요청 단위 기준 |
| External observation | 외부 호출에서 관찰한 결과 | DSP 응답, no-bid, timeout, late response |
| Valid candidate | 내부 검증을 통과한 판단 대상 | winner 후보가 될 수 있는 bid |
| Result message | 호출자에게 반환하는 결과 | winner 또는 no-winner 결과 |
| Observability data | 성능과 원인 분석을 위한 자료 | latency, timeout rate, 결과 분포 |

## 3. Hot Path Data Flow

| Step | Data role | Meaning |
|---|---|---|
| 1 | Provider-facing slot input | 광고 슬롯이 열렸다는 외부 입력이다. |
| 2 | Supply serving data | SSP가 provider, placement, media constraint를 해석하기 위해 읽는 기준 데이터다. |
| 3 | Auction decision context | 경매 실행에 필요한 요청 단위 내부 문맥이다. |
| 4 | DSP-facing bid request | SSP가 DSP에 전달하는 입찰 요청이다. |
| 5 | DSP call observation | DSP 호출에서 관찰한 응답, 무응답, 지연, 오류 상태다. |
| 6 | Bid observation | DSP가 반환한 입찰 응답이다. 검증 전에는 외부 관찰값이다. |
| 7 | Valid bid candidate | deadline과 bid validation을 통과한 winner 후보이다. |
| 8 | Provider-facing auction result | winner 또는 no-winner를 호출자에게 반환하는 결과다. |

## 4. Current Data Boundaries

현재 hot path에서 직접 다루는 데이터는 요청 단위 판단에 집중한다.

| Boundary | Rule |
|---|---|
| Provider-facing input | DSP-facing 입찰 요청과 같은 데이터로 취급하지 않는다. |
| SSP-DSP message | OpenRTB subset을 사용한다. |
| DSP response observation | 호출 결과와 유효 후보 판단을 분리한다. |
| Winner decision | 유효 bid 후보만 입력으로 받는다. |
| Observability | 성능과 원인 분석을 위한 보조 데이터다. |

광고 지면 기준 데이터와 캠페인 판단 기준 데이터는 hot path에서 빠르게 읽기 위한 serving data다. 현재 구현에서는 재현 가능한 in-memory 데이터로 둘 수 있지만, 운영 원본 데이터와 같은 책임으로 보지 않는다.

## 5. Core Invariants

| Invariant | Why it matters |
|---|---|
| 경매를 시작할 수 없는 provider 요청은 DSP 호출까지 가지 않는다. | 불필요한 fan-out과 모호한 실패를 막는다. |
| 경매 실행 문맥은 생성 이후 경매 종료까지 의미가 바뀌지 않는다. | deadline, validation, result explanation이 안정된다. |
| DSP-facing 입찰 요청과 내부 경매 문맥은 같은 request, impression, media, floor, currency를 가리킨다. | 뒤 단계가 요청 의미를 다시 추론하지 않게 한다. |
| DSP 호출 관찰값은 유효 bid 후보가 아니다. | no-bid, timeout, late bid, invalid bid가 winner selection에 섞이지 않는다. |
| winner는 반드시 유효 bid 후보에서만 나온다. | 경매 결과의 정확성을 지킨다. |
| valid bid가 없으면 정상 no-winner 결과가 된다. | 시스템 장애와 비낙찰을 구분한다. |
| Observability data는 경매 결과의 원본이 아니다. | metrics 누락이나 샘플링이 비즈니스 결과를 바꾸면 안 된다. |

## 6. State Transitions

### Auction Lifecycle

| From | To | Rule |
|---|---|---|
| Provider slot input | Request rejected | 요청이 경매 실행 조건을 만족하지 않는다. |
| Provider slot input | Auction context accepted | 요청과 기준 데이터가 경매 실행 가능 상태로 정규화된다. |
| Auction context accepted | Waiting for DSP results | SSP가 deadline을 정하고 DSP 호출을 시작한다. |
| Waiting for DSP results | Judging bids | deadline 도달 또는 모든 결과 관찰 후 DSP 결과를 검증한다. |
| Judging bids | Winner result | valid candidate가 있고 winner rule이 적용된다. |
| Judging bids | No-winner result | valid candidate가 없다. |

### DSP Result Classification

| Observation | Classification | Candidate? |
|---|---|---|
| Explicit no-bid | `NO_BID` | No |
| No response before deadline | `TIMEOUT` | No |
| Response after deadline | `LATE_BID` | No |
| Decode or transport failure | `ERROR` | No |
| Bid response fails validation | `INVALID_BID` | No |
| Bid response passes validation before deadline | valid candidate | Yes |

## 7. Consistency Requirements

현재 hot path에서 필요한 정합성은 요청 단위 정합성이다.

| Consistency concern | Requirement |
|---|---|
| Request consistency | provider-facing 입력, DSP-facing 입찰 요청, 내부 경매 문맥이 같은 경매 의미를 가져야 한다. |
| Candidate consistency | DSP 입찰 응답은 request id, impression id, media type, bidfloor, currency, deadline 검증을 통과해야 후보가 된다. |
| Winner consistency | winner는 valid candidate 중에서만 결정된다. |
| Result consistency | no-winner는 유효 후보가 없을 때의 정상 결과다. |
| Diagnostic consistency | metrics/logs/traces는 결과 설명을 돕지만 경매 결과의 원본은 아니다. |
