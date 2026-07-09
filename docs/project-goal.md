# Project Goal

## 1. 동기

이 프로젝트의 목적은 RTB 광고 제품 전체를 구현하는 것이 아니라, 백엔드 성능 엔지니어링에서 중요한 두 가지 문제를 작게 재현하고 검증하는 것이다.

- 제한 시간 안에서의 응답 지연 관리
- 고빈도 요청에서의 동시 처리 한계 분석

RTB는 이 목적에 적합한 도메인이다. 광고 슬롯 요청은 짧고 자주 발생하는 호출로 모델링하기 좋고, 하나의 경매 요청은 여러 DSP 호출로 fan-out된다. 또한 늦게 도착한 bid는 좋은 가격이어도 사용할 수 없기 때문에, 평균 응답 시간만으로 시스템을 평가할 수 없다.

따라서 이 프로젝트는 p95/p99 latency, deadline 준수율, timeout rate, DSP별 응답 분포, HTTP/JSON/경매/fan-out baseline을 기준으로 성능을 관찰하고 병목을 설명하는 것을 목표로 한다.

## 2. 선택한 RTB 문제

이 프로젝트는 광고 슬롯이 열렸을 때 SSP가 여러 DSP에 입찰을 요청하고, 제한 시간 안에 도착한 응답만으로 낙찰 여부를 결정하는 경매 실행 경로를 다룬다.

핵심 문제는 다음과 같다.

> SSP가 provider-facing 요청을 검증하고 inventory 기준으로 DSP-facing OpenRTB `BidRequest`를 만든 뒤, 여러 경량 DSP의 응답을 제한 시간 안에 수집하고, `no-bid`, `timeout`, `late bid`, `invalid bid`를 구분해 유효한 bid 중 winner 또는 no-winner를 결정하는 것.

이 문제는 전체 광고 플랫폼을 구현하지 않아도, 낮은 레이턴시 제약과 동시 외부 호출이 만드는 성능 압력을 보여준다.

## 3. 어필할 역량

이 프로젝트는 다음 역량을 보여줘야 한다.

| 역량 | 보여줘야 하는 것 |
|---|---|
| 응답 지연 관리 | 평균 응답 시간이 아니라 p95/p99 latency와 deadline 준수율로 hot path를 평가한다. |
| 동시 처리 설계 | 하나의 요청이 여러 DSP 호출로 fan-out될 때 in-flight 작업, thread, connection, 외부 호출 비용을 고려한다. |
| 관측 기반 병목 분석 | HTTP baseline, JSON baseline, 경매 baseline, fan-out baseline을 분리해 어느 구간이 느린지 설명한다. |
| 결과 품질 유지 | timeout, late bid, invalid bid가 winner 후보에 섞이지 않고, 유효 bid가 없으면 정상 no-winner가 된다. |

## 4. 성공 기준

현재 단계에서 이 프로젝트가 성공했다고 말하려면 최소한 다음을 보여줘야 한다.

- `Provider Slot Request`부터 `AuctionResult`까지의 경계가 코드와 문서에서 일관된다.
- SSP는 같은 `BidRequest`를 여러 DSP에 전달하고 제한 시간 안에 응답을 수집한다.
- `no-bid`, `timeout`, `late bid`, `invalid bid`, `no-winner`가 구분된다.
- winner는 valid bid 후보에서만 결정된다.
- p95/p99 latency, deadline 준수율, timeout rate, DSP별 응답 분포를 측정할 수 있다.
- HTTP/JSON/경매/fan-out baseline을 분리해 병목을 설명할 수 있다.
