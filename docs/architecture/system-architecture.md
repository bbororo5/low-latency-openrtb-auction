# RTB Auction System Architecture

상태: Active 1.0
범위: 현재 Accepted ADR이 정의한 v1 baseline 구조

이 문서는 ADR의 결정 결과를 구현 가능한 구성요소, 책임, runtime 흐름, resource boundary로 표현한다. 결정 근거와 대안 비교는 [ADR Register](decisions/architecture-decision-register.md)를 따른다.

## 1. Stakeholder Roles and Uses

개인 프로젝트이므로 한 사람이 여러 역할을 수행하지만 관심사는 역할별로 분리한다.

| Role | Architecture use |
|---|---|
| Owner/developer | 범위, 도메인 규칙, 변경 비용 판단 |
| Integrator | provider 및 OpenRTB subset 계약 확인 |
| Performance tester | 고정 topology, workload, pass condition 확인 |
| Operator | metric, saturation, recovery 신호 확인 |
| Maintainer | package 책임과 ADR revisit trigger 확인 |

## 2. System Context

| Element | Responsibility | Boundary |
|---|---|---|
| Provider client | 광고 슬롯 요청과 `effectiveTmax` 제공 | Project-specific HTTP/JSON |
| SSP | 요청 검증, OpenRTB 변환, DSP fan-out, 결과 분류, winner 결정 | 이 프로젝트의 중심 system-of-interest |
| DSP | startup campaign snapshot에서 후보 검색, 가격 결정, bid/no-bid 반환 | OpenRTB 2.6 subset HTTP/JSON |
| Snapshot input | process 시작 전에 inventory/campaign 값을 제공 | 현재는 bootstrap fixture; 원본 관리·동기화는 범위 밖 |
| Metrics backend | Prometheus 형식 metric 수집과 시각화 | 동기 경매 결과에 영향 없음 |

## 3. Building Blocks

| Module/package | Responsibility | Must not own |
|---|---|---|
| `ssp.slotrequest` | provider request 검증, inventory lookup | DSP transport, winner policy |
| `ssp.bidrequest` | auction context와 OpenRTB request template 생성 | deadline 대기, HTTP 호출 |
| `ssp.auctionflow` | deadline 계산과 단계 orchestration | codec 또는 transport 세부사항 |
| `ssp.dspgateway` | bounded fan-out, HTTP observation, timeout/error mapping | bid validity와 winner 선택 |
| `ssp.bidjudge` | DSP당 terminal classification과 valid candidate 생성 | network lifecycle |
| `ssp.winnerselector` | first-price와 deterministic tie-break | 외부 응답 해석 |
| `ssp.inventory` | immutable inventory serving snapshot | 원본 관리와 runtime reload |
| `dsp.campaignlookup` | immutable campaign serving snapshot 조회 | 원본 campaign system 호출 |
| `dsp.matcher/pricing/bidbuilder` | match, bid price, OpenRTB response 생성 | HTTP server lifecycle |
| `shared.openrtb` | 명시적 OpenRTB wire subset과 codec | provider/domain model 통합 |
| `shared.observability` | bounded-cardinality aggregate metric | winner 또는 terminal policy |

## 4. Auction Runtime Flow

1. SSP web adapter는 body decode 전에 `receivedAt`을 기록한다.
2. provider request를 검증하고 immutable inventory snapshot에서 placement를 읽는다.
3. auction context와 OpenRTB request template을 생성한다. context의 `effectiveTmax`로 `bidCutoff`를 계산한다.
4. fan-out 직전에 모든 DSP에 공통인 `outboundTmax = bidCutoff - dispatchAt`을 계산한다. deadline이 이미 끝났으면 DSP 호출을 시작하지 않는다.
5. 선택된 DSP마다 같은 payload를 한 번씩 bounded executor를 통해 전송한다.
6. global deadline까지 완료를 기다리고 미완료 future를 취소한다. 취소 또는 cutoff 이후 관찰은 `TIMEOUT`이다.
7. gateway observation을 judge가 DSP당 하나의 `VALID_BID`, `NO_BID`, `TIMEOUT`, `INVALID_BID`, `ERROR`로 확정한다.
8. `VALID_BID` 후보 중 최고 가격을 선택하고, 동가이면 `dspId`, `bidId` 오름차순으로 결정한다.
9. provider에 outcome, winner, elapsed time, 상호 배타적인 terminal summary를 반환한다.

현재 baseline은 취소한 HTTP 요청의 사후 응답을 별도 async lifecycle로 유지하지 않는다. 따라서 `LATE_RESPONSE` 진단보다 자원 회수와 작은 상태 공간을 우선한다. 사후 관측이 필요해지면 `ADR-001`을 재검토한다.

## 5. Runtime and Resource Boundaries

| Resource | Baseline policy | Signal |
|---|---|---|
| SSP inbound request | JDK `HttpServer`, virtual thread per exchange | JVM thread/runtime metrics |
| DSP HTTP work | bounded platform-thread pool | active, pool, queued, completed, rejected |
| DSP task queue | bounded `ArrayBlockingQueue` | queued, rejected |
| DSP call lifecycle | one call per selected DSP, global deadline cancellation | in-flight, latency, result |
| DSP terminal classification | judge 확정 후 DSP별 1회 기록 | `rtb_ssp_dsp_terminal_result_total` |
| HTTP connection | shared JDK `HttpClient`, HTTP/1.1 reuse | indirect latency/error signals |
| Telemetry cardinality | fixed result/media/DSP tags only | metric schema inspection |

## 6. Deployment Baseline

- SSP 1 process와 독립 DSP processes를 private network에 배치한다.
- reference performance에서는 load generator를 target host와 분리한다.
- inventory/campaign snapshot은 각 process startup에 주입하며 runtime shared database는 없다.
- Prometheus/Grafana는 측정 구성요소이며 경매의 필수 응답 경로가 아니다.
- multi-region, autoscaling, Kubernetes는 현재 구조의 가정이 아니다.

## 7. Conformance

| Contract | Automated evidence |
|---|---|
| package dependency direction | ArchUnit module/C3 tests |
| deterministic winner | `FirstPriceWinnerSelectorTest` 100 shuffled orders |
| exclusive terminal results | `DefaultBidJudgeTest` |
| remaining OpenRTB `tmax` | `HttpDspGatewayTest` |
| provider→SSP→DSP runtime flow | root `AuctionSmokeE2eTest` |
| bounded executor rejection/recovery | `DspHttpExecutorTest` |
| reference latency/recovery | `VP-002~004`; evidence pending |
