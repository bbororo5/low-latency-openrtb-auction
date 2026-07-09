# ADR-002: Use JDK HttpClient for Baseline DspGateway

## Status

Accepted for baseline. Revisit after measurement.

## Context

`DspGateway`는 SSP에서 여러 DSP endpoint로 OpenRTB `BidRequest`를 보내고, 응답을 `DspCallResult`로 분류한다.

필요한 기능은 다음과 같다.

- HTTP POST
- JSON request/response body
- 비동기 fan-out
- request timeout
- persistent connection reuse
- HTTP/1.1 baseline
- HTTP/2 실험 가능성

현재 프로젝트는 Java 21 기반이며, 아직 Spring WebFlux, Netty, Apache HttpClient 같은 네트워크 stack을 사용하지 않는다.

## Decision Drivers

- Java 21 compatibility
- implementation simplicity
- async fan-out support
- timeout support
- HTTP/2 experiment path
- dependency cost
- future replacement cost

## Options Considered

| Option | Pros | Cons | Decision |
|---|---|---|---|
| JDK HttpClient | Java 21 표준, 외부 의존성 없음, async 지원, HTTP/2 실험 가능 | connection pool 세부 제어 제한 | Baseline |
| Apache HttpClient 5 | connection pool/timeout 제어 강함 | 의존성 및 설정 복잡도 증가 | Defer |
| Reactor Netty | 고성능 async, pool 설정 풍부 | reactive stack 도입 비용 큼 | Defer |
| Netty 직접 사용 | 세밀한 최적화 가능 | 구현 복잡도 과함 | Reject for now |
| OkHttp | 사용성 좋고 pool 성숙 | 현재 Java 서버 stack 기준 주력 선택 근거 약함 | Defer |

## Analysis

DspGateway의 초기 위험은 네트워크 라이브러리의 한계보다 다음 구현 요소에 있다.

```text
- deadline 기반 timeout 계산
- 여러 DSP endpoint로 병렬 fan-out
- HTTP status와 body를 DspCallResult로 분류
- OpenRTB JSON 직렬화/역직렬화
- connection 재사용
```

JDK `HttpClient`는 Java 표준 API로 HTTP/1.1, HTTP/2, async 요청을 지원한다. 따라서 baseline 구현과 초기 측정에는 충분하다.

다만 connection pool을 세밀하게 제어해야 하거나, network layer가 p95/p99 병목으로 확인되면 Apache HttpClient 5 또는 Reactor Netty를 재검토한다.

## Decision

Baseline 구현은 Java 표준 `java.net.http.HttpClient`를 사용한다.

사용 방식:

- `HttpClient` 인스턴스를 재사용한다.
- `sendAsync`로 DSP fan-out을 구현한다.
- `HttpRequest.timeout`에 auction deadline에서 남은 시간을 반영한다.
- HTTP status와 body를 `DspCallResult`로 변환한다.
- HTTP/2는 `HttpClient.Version.HTTP_2` 설정으로 실험 가능하게 남긴다.

## Consequences

Positive:

- 구현 복잡도를 낮춘다.
- Java 21 표준 API로 baseline을 빠르게 만들 수 있다.
- HTTP/1.1과 HTTP/2를 같은 API로 비교할 수 있다.
- 외부 의존성을 늘리지 않는다.

Negative:

- connection pool을 세밀하게 튜닝하기 어렵다.
- 고성능 네트워크 튜닝 단계에서는 한계가 있을 수 있다.
- Apache HttpClient 5나 Reactor Netty 대비 운영 설정 옵션이 적다.

## Revisit Conditions

다음 중 하나가 측정되면 재검토한다.

- connection acquisition 또는 connection reuse가 병목으로 보인다.
- DSP fan-out p95/p99 latency가 timeout budget을 지속적으로 침범한다.
- HTTP/2 multiplexing 실험이 필요하다.
- JDK HttpClient의 timeout/pool 제어가 부족하다.
- 관찰성 지표상 네트워크 계층이 주요 병목으로 확인된다.
