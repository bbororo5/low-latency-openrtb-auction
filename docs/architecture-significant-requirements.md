# Architecture Significant Requirements

이 문서는 RTB hot path의 아키텍처를 결정하기 전에 반드시 확인해야 하는 요구사항과 제약사항을 정리한다.

ASR은 기능 목록이 아니다. 이 문서의 목적은 컴포넌트 경계, 데이터 경계, timeout 정책, 동시성 제어, 저장소 선택, 관측 방식이 충돌할 때 사용할 판단 기준을 세우는 것이다.

## 1. Problem Context

이 프로젝트는 광고 슬롯 요청이 들어왔을 때 SSP가 여러 DSP에 입찰 요청을 보내고, 제한 시간 안에 도착한 유효 응답만으로 winner 또는 no-winner를 결정하는 RTB hot path를 다룬다.

이 문제는 단순히 가장 높은 가격을 고르는 문제가 아니다.

- 늦게 도착한 bid는 높은 가격이어도 winner 후보가 될 수 없다.
- invalid bid, no-bid, timeout은 winner 후보가 될 수 없다.
- 일부 DSP 실패가 전체 경매 실패로 전파되면 안 된다.
- 입찰 가격과 실제 돈의 차감/원장 기록은 같은 책임이 아니다.
- 평균 latency만으로 deadline 위반과 tail latency를 설명할 수 없다.

따라서 이 시스템의 아키텍처는 결과 정확성, 돈 데이터 경계, deadline, tail latency, 동시성, 부분 실패, 중복 처리, 시간 기준, 관측 가능성을 함께 고려해야 한다.

## 2. Adopted Architecture Characteristics

아래 특성은 현재 프로젝트의 아키텍처를 실제로 제약한다. 이 문서는 1부터 10까지의 정밀한 순위를 주장하지 않는다. 대신 후속 설계에서 방어 가능한 의사결정을 위해 세 개의 decision group으로 나눈다.

| Decision group | Characteristic | Meaning |
|---|---|---|
| Correctness constraints | Result correctness | valid bid만 winner 후보가 된다. |
| Correctness constraints | Monetary boundary | bid price, budget, balance, charge, ledger를 같은 데이터로 취급하지 않는다. |
| Correctness constraints | Idempotency boundary | 요청 재시도나 win event 중복이 중복 경매/중복 과금으로 이어지지 않아야 한다. |
| Correctness constraints | Time consistency | deadline, timeout, late bid 판정은 일관된 시간 기준을 사용해야 한다. |
| Correctness constraints | Deterministic decision | 같은 valid bid 후보 집합에서는 같은 winner가 나와야 한다. |
| Performance constraints | Deadline compliance | 더 좋은 bid를 기다리는 것보다 제한 시간 안에 결정을 끝내는 것이 우선이다. |
| Performance constraints | Tail latency control | 평균 latency보다 p95/p99와 deadline 위반을 더 중요하게 본다. |
| Performance constraints | Throughput under concurrency | 고빈도 요청과 DSP fan-out에서 처리량과 포화 지점을 관찰할 수 있어야 한다. |
| Failure handling and observability | Failure isolation | 일부 DSP timeout/error가 전체 경매 실패로 번지지 않아야 한다. |
| Failure handling and observability | Observability | winner/no-winner와 latency 문제를 결과 분포와 지표로 설명할 수 있어야 한다. |

`Security / privacy`, `Operability`, `Evolvability`는 중요하지만 현재 hot path 아키텍처의 1차 결정 요인은 아니다. 후속 API, runtime, operations 문서에서 필요한 수준으로 다룬다.

decision group 간 판단 기준은 다음과 같다.

- `Correctness constraints`는 깨지면 시스템이 빠르게 동작해도 경매 의미가 틀어진다.
- `Performance constraints`는 이 프로젝트의 핵심 동기이며, correctness를 깨지 않는 범위에서 우선 최적화한다.
- `Failure handling and observability`는 부분 실패를 견디고 결과를 설명하기 위한 조건이다.

## 3. Core Invariants

아래 불변조건은 구현 방식이 바뀌어도 유지되어야 한다.

| Invariant | Reason |
|---|---|
| 경매를 시작할 수 없는 provider 요청은 DSP 호출까지 가지 않는다. | 불필요한 fan-out과 모호한 실패를 막는다. |
| 하나의 경매 실행은 하나의 deadline을 기준으로 판단된다. | DSP별 응답을 같은 시간 기준으로 비교한다. |
| DSP 응답은 검증 전까지 winner 후보가 아니다. | 외부 관찰값과 내부 판단 대상을 분리한다. |
| winner는 valid bid 후보에서만 결정된다. | late bid, invalid bid, no-bid, timeout이 결과를 오염시키지 않는다. |
| valid bid가 없으면 no-winner가 된다. | 정상 비낙찰과 시스템 장애를 구분한다. |
| bid price는 낙찰 판단의 입력이지 잔고 차감의 원장이 아니다. | auction result와 monetary ledger의 책임을 분리한다. |
| 같은 낙찰 사실은 돈 관점에서 한 번만 반영되어야 한다. | 향후 과금/정산 처리에서 중복 차감을 막는다. |
| 같은 valid bid 후보 집합과 같은 tie-break 기준은 같은 winner를 만든다. | 결과 재현성과 테스트 가능성을 보장한다. |
| 관측 데이터는 경매 결과나 과금 사실의 원본이 아니다. | metrics/logs/traces 누락이나 샘플링이 비즈니스 사실을 바꾸면 안 된다. |

## 4. Tradeoff Direction

이 프로젝트는 아래 방향으로 손실을 선택한다.

| Tradeoff | Direction |
|---|---|
| Bid opportunity vs deadline | 더 많은 bid를 기다리지 않고 deadline을 지킨다. |
| Auction speed vs monetary consistency | winner decision은 빠르게 끝내되, 돈의 진실원은 별도 책임으로 둔다. |
| Throughput vs resource safety | 무제한 fan-out보다 in-flight 작업과 외부 호출 비용을 관찰하고 제한할 수 있게 한다. |
| Simplicity vs correctness | 단순 성공/실패 모델 대신 no-bid, timeout, late bid, invalid bid, no-winner를 구분한다. |
| Observability vs hot path cost | 관측은 필수지만, 경매 결과를 바꾸지 않는 보조 데이터로 둔다. |
| Freshness vs latency | source store 동기 조회를 hot path 기본값으로 두지 않는다. |

## 5. Architecture Implications

이 ASR은 후속 설계에 다음 압력을 준다.

| Area | Implication |
|---|---|
| Component boundary | slot input, bid request creation, DSP fan-out, bid judgment, winner decision 책임을 분리한다. |
| Data boundary | DSP response observation, valid bid candidate, auction result, win fact, ledger entry를 구분한다. |
| Monetary model | bid price와 실제 잔고 차감/원장 기록은 같은 데이터로 취급하지 않는다. |
| Identity model | request identity, auction identity, win event identity의 의미를 구분해야 한다. |
| Time model | deadline과 late bid 판정에 쓰는 시간 기준을 정의해야 한다. |
| Resource model | 동시 요청, DSP 수, connection, thread, serialization 비용을 측정하고 제한할 수 있어야 한다. |
| API contract | no-bid, timeout, late bid, invalid bid, no-winner의 의미가 경계에서 흔들리면 안 된다. |
| Verification | 평균 latency만 보지 않고 p95/p99, deadline compliance, timeout rate, result distribution을 본다. |
