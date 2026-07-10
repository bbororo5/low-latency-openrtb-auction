# Development Requirements Baseline

상태: Baseline 1.0
적용 범위: 경량 SSP와 경량 DSP로 구성된 RTB 경매 hot path
규범 수준: 이 문서의 `FR`, `QR`, `C`, `OOS` 항목은 후속 ASR, Driver, ADR의 원천이다.

## 1. Project Goals

| ID | Goal | Success evidence |
|---|---|---|
| `G-001` | 제한 시간 안에서 여러 DSP 응답을 다루는 경매 경로를 구현한다. | 늦거나 잘못된 bid가 winner가 되지 않고, reference workload의 latency 기준을 만족한다. |
| `G-002` | DSP fan-out이 만드는 동시성·자원 한계를 측정하고 설명한다. | 안정 처리 구간, 최초 실패 지점, 병목 신호, overload 후 회복 여부를 재현한다. |
| `G-003` | 최적화가 결과 정확성을 훼손하지 않았음을 증명한다. | 성능 전후에 동일한 도메인 검증을 통과하고 결과 분류가 유지된다. |

이 프로젝트의 성공은 최대 RPS 하나로 판단하지 않는다. 결과 정확성, deadline 의미, tail latency, resource saturation, 회복 동작을 함께 설명해야 한다.

## 2. System Boundary

### Included

- provider-facing 광고 슬롯 요청 수신과 검증
- 하나의 impression을 가진 OpenRTB 2.6 subset `BidRequest` 생성
- 설정된 여러 DSP에 대한 bid 요청 fan-out
- DSP의 bid, no-bid, timeout, invalid response, transport/application error 처리
- 유효 bid 후보 검증
- first-price winner 또는 정상 no-winner 결정
- 경매와 DSP 호출의 latency, 결과, 포화 신호 측정
- 재현 가능한 기능·부하·장애 시나리오

### Excluded

| ID | Out of scope |
|---|---|
| `OOS-001` | 광고 렌더링, impression/click tracking, attribution |
| `OOS-002` | 사용자 식별, 프로파일링, 개인정보 처리 |
| `OOS-003` | budget reservation, billing, settlement, ledger, reconciliation |
| `OOS-004` | inventory/campaign 원본 관리, 관리자 API, 데이터 동기화 파이프라인 |
| `OOS-005` | 외부 SSP/DSP 인증, 계약 적합성 인증, 인터넷 구간 보안 운영 |
| `OOS-006` | multi-region HA, Kubernetes, autoscaling, DSP routing optimization |
| `OOS-007` | win event와 money event의 exactly-once/idempotency 설계 |

범위 밖 항목은 현재 ASR이나 ADR의 필수 기준으로 사용하지 않는다.

## 3. Canonical Semantics

### 3.1 Time boundaries

- `receivedAt`: SSP web adapter가 요청 처리를 시작하고 body를 해석하기 전의 시각이다.
- `effectiveTmax`: 요청의 양수 `tmax`가 있으면 그 값, 없으면 placement 기본값이다.
- `bidCutoff`: `receivedAt + effectiveTmax`다.
- `on-time response`: SSP가 `bidCutoff` 이하의 시각에 수신한 DSP 응답이다.
- `provider latency`: load generator가 provider 요청을 시작한 시점부터 응답 body를 모두 받은 시점까지다.

`bidCutoff`는 winner 후보 자격의 경계다. provider 응답 latency 목표는 별도의 품질 요구와 verification profile로 검증한다.

### 3.2 Auction outcome and DSP result

경매 결과와 DSP별 결과는 서로 다른 계층이다.

| Layer | Values | Rule |
|---|---|---|
| Auction outcome | `WINNER`, `NO_WINNER`, `INVALID_REQUEST`, `UNSUPPORTED_REQUEST` | 한 요청은 하나의 outcome을 가진다. |
| DSP terminal result at cutoff | `VALID_BID`, `NO_BID`, `TIMEOUT`, `INVALID_BID`, `ERROR` | 호출을 시작한 DSP마다 정확히 하나를 가진다. |
| Post-cutoff diagnostic | `LATE_RESPONSE` | 동기 `AuctionResult`의 terminal result를 바꾸지 않는 관측 이벤트다. |

`TIMEOUT`은 `bidCutoff`까지 terminal response가 없었다는 뜻이다. 그 이후 응답을 관측할 수 있으면 `LATE_RESPONSE` metric/log로 남길 수 있지만, 이미 반환한 결과와 winner를 바꾸지 않는다.

## 4. Functional Requirements

| ID | Priority | Requirement | Acceptance condition |
|---|---:|---|---|
| `FR-001` | P0 | SSP는 provider, placement, media type과 media constraint를 검증하고, 실행할 수 없는 요청을 DSP 호출 전에 거절해야 한다. | 거절 시 DSP call count가 0이며 `INVALID_REQUEST` 또는 `UNSUPPORTED_REQUEST`가 구분된다. |
| `FR-002` | P0 | SSP는 유효한 provider request와 inventory snapshot으로 immutable auction context와 OpenRTB `BidRequest` 하나를 생성해야 한다. | context와 `BidRequest`가 같은 request, impression, media, floor, currency, `tmax`를 가리킨다. |
| `FR-003` | P0 | SSP는 한 경매에서 선택된 각 DSP에 의미상 동일한 `BidRequest`를 최대 한 번 전송해야 한다. | DSP별 request/imp ID와 payload 의미가 동일하고 암묵적 retry가 없다. |
| `FR-004` | P0 | SSP는 `bidCutoff` 시점에 각 DSP 호출을 하나의 terminal result로 분류해야 한다. | terminal result count 합이 시작된 DSP call count와 같다. |
| `FR-005` | P0 | SSP는 on-time bid의 request ID, impression ID, price/floor, currency, media type, 필수 markup을 검증해야 한다. | 하나라도 실패하면 `INVALID_BID`이며 winner 후보가 아니다. |
| `FR-006` | P0 | SSP는 `VALID_BID` 중 가장 높은 가격을 winner로 선택하는 first-price auction을 수행해야 한다. | `auctionPrice == winningPrice`; 동가이면 `dspId`, `bidId` 오름차순으로 결정한다. |
| `FR-007` | P0 | `VALID_BID`가 없으면 정상 `NO_WINNER`를 반환해야 한다. | timeout/no-bid/invalid/error만 있는 경우 HTTP transport 성공과 `NO_WINNER`가 함께 성립한다. |
| `FR-008` | P0 | 일부 DSP의 timeout, invalid response, error는 다른 DSP의 유효 bid 처리를 중단시키면 안 된다. | 유효 후보가 하나 이상이면 실패한 DSP와 무관하게 올바른 winner를 반환한다. |
| `FR-009` | P1 | 경량 DSP는 준비된 campaign snapshot을 기준으로 bid 또는 no-bid를 반환해야 한다. | 요청 처리 중 campaign source system을 동기 호출하지 않는다. |
| `FR-010` | P1 | provider 결과는 outcome, winner 정보, elapsed time, DSP terminal result summary를 제공해야 한다. | summary는 상호 배타적이며 호출한 DSP 수와 일치한다. |

HTTP transport 수준에서 malformed JSON은 `400`, 미지원 method는 `405`, 처리하지 못한 내부 오류는 `5xx`로 응답한다. 이들은 정상 경매 outcome이 아니다.

## 5. Quality Requirements

| ID | Priority | Requirement | Measure |
|---|---:|---|---|
| `QR-001` | P0 | bid 후보 자격은 `bidCutoff`를 엄격히 따라야 한다. | cutoff 이후 응답이 winner가 되는 경우 0건; `VP-001`, `VP-003` domain checks 100%. |
| `QR-002` | P0 | provider-facing hot path는 reference workload에서 tail latency 목표를 만족해야 한다. | `VP-002` p99 ≤ 120 ms, `VP-003` p99 ≤ 150 ms. |
| `QR-003` | P0 | DSP 부분 실패가 전체 경매 실패로 전파되지 않아야 한다. | `VP-003` HTTP failure 0%, domain checks 100%. |
| `QR-004` | P0 | thread, task queue, in-flight DSP call은 유한한 상한과 관측 수단을 가져야 한다. | 무제한 executor/queue 사용 금지; profile 종료 1초 후 in-flight DSP call 0. |
| `QR-005` | P0 | overload 이후 프로세스 재시작 없이 정상 처리 상태로 회복해야 한다. | `VP-004`에서 부하를 1 RPS로 낮춘 뒤 30초 안에 연속 30건 domain checks 100%. |
| `QR-006` | P0 | 같은 auction context와 같은 valid candidate 집합은 같은 결과를 만들어야 한다. | 입력 순서를 바꾼 반복 테스트 100회에서 winner와 auction price가 동일하다. |
| `QR-007` | P1 | 시스템은 auction/DSP latency, outcome, DSP terminal result, in-flight, executor active/queued/rejected 상태를 측정해야 한다. | 모든 signal이 metric 또는 구조화 log로 조회되고, request/bid ID는 metric tag로 사용하지 않는다. |
| `QR-008` | P1 | 필수 관측 기능은 hot path 성능을 지배하지 않아야 한다. | 같은 환경·부하에서 관측 on/off p99 차이가 10% 이하이다. |
| `QR-009` | P1 | 성능 결과는 환경과 workload를 고정해 반복 가능해야 한다. | 결과에 commit, 환경, topology, payload, DSP behavior, RPS, duration, p95/p99, 오류·드롭·domain check를 기록한다. |

P0는 기준선 채택과 ADR 결정을 막는 필수 요구다. P1은 성능 해석과 유지보수를 위한 필수 보조 요구다.

## 6. Constraints and Assumptions

### Constraints

| ID | Constraint |
|---|---|
| `C-001` | SSP-DSP wire boundary는 OpenRTB 2.6 semantics의 명시적 subset과 HTTP/JSON을 사용한다. |
| `C-002` | provider-facing API는 프로젝트 전용 계약이며 OpenRTB 객체를 직접 받지 않는다. |
| `C-003` | 초기 구현과 검증 runtime은 Java 21이다. |
| `C-004` | baseline auction은 one impression, banner/video, USD, first-price만 지원한다. |
| `C-005` | reference workload의 기본 `effectiveTmax`는 120 ms다. |
| `C-006` | reference workload는 provider-facing 경로를 사용한다. direct OpenRTB ingress는 제품 요구가 아닌 진단용 adapter다. |

### Assumptions

| ID | Assumption | Invalidated when |
|---|---|---|
| `A-001` | inventory와 campaign snapshot은 요청 전에 준비되어 있다. | freshness/reload가 현재 release 범위에 들어올 때 |
| `A-002` | fan-out 대상 DSP endpoint는 요청 전에 구성되어 있다. | 동적 routing/discovery를 도입할 때 |
| `A-003` | reference benchmark는 격리된 private network에서 수행하며 TLS 비용은 제외한다. | 인터넷/TLS 경로를 성능 범위에 넣을 때 |
| `A-004` | load generator는 목표 arrival rate를 만들 수 있는 별도 host에서 실행한다. | dropped iteration이나 client saturation이 관찰될 때 |

## 7. Verification Profiles

### Reference environment

| Item | Value |
|---|---|
| Target | Linux x86_64, 2 vCPU, 8 GiB memory |
| Topology | SSP 1 instance와 profile에 명시된 DSP instances |
| Load generator | 별도 host, target과 같은 region/private network |
| Protocol | provider HTTP/JSON → SSP → DSP HTTP/JSON |
| Observability | 필수 metrics enabled |

이 환경은 production SLO가 아니라 v1 engineering acceptance baseline이다.

### `VP-001` Functional correctness

- invalid/unsupported provider request는 DSP 호출 없이 거절
- on-time valid bid 중 최고가 winner
- highest-price bid가 late 또는 invalid이면 제외
- 모든 DSP가 no-bid/timeout/error이면 정상 `NO_WINNER`
- 같은 가격 후보의 입력 순서를 바꿔도 같은 winner

### `VP-002` Steady capacity baseline

| Item | Value |
|---|---|
| Request | banner 300×250, USD, `tmax=120ms` |
| DSP topology | 2 valid bid + 1 no-bid |
| Load | 100 RPS, constant arrival rate, 2 minutes |
| Pass | checks 100%, HTTP failure 0%, dropped iteration 0, p99 ≤ 120 ms |

### `VP-003` Timeout resilience baseline

| Item | Value |
|---|---|
| Request | banner 300×250, USD, `tmax=120ms` |
| DSP topology | 2 valid bid + 1 no-bid + 1 response delayed to 500 ms |
| Load | 50 RPS, constant arrival rate, 2 minutes |
| Pass | checks 100%, HTTP failure 0%, dropped iteration 0, p99 ≤ 150 ms, timeout terminal result 1/request |

지연 DSP의 사후 응답을 관측하더라도 동기 AuctionResult와 winner는 바뀌지 않는다.

### `VP-004` Overload and recovery

1. `VP-002`의 RPS를 25씩 높여 P0 pass condition이 처음 깨지는 지점을 찾는다.
2. 최초 실패 RPS의 2배를 30초 동안 가한다.
3. 부하를 1 RPS로 낮추고 30초 동안 유지한다.
4. 재시작 없이 연속 30건이 올바른 결과를 반환하고 executor rejection 증가가 멈추며 in-flight가 정상 범위로 돌아와야 한다.

## 8. Change Policy

- reference 수치는 구현 결과에 맞춰 낮추지 않는다. 변경하려면 요구사항 변경 이유를 기록한다.
- 새로운 media type, multi-imp, money/event 처리는 별도 요구사항과 ASR 검토 후 범위에 추가한다.
- ADR은 이 문서의 요구를 완화할 수 없다. 불가피하면 먼저 요구사항을 변경한다.
