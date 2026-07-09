# ADR-001: Use OpenRTB-Compatible HTTP Transport for DSP Bid Calls

## Status

Accepted for baseline. Revisit after measurement.

## Context

SSP는 하나의 OpenRTB `BidRequest`를 여러 DSP에 전달하고, 제한 시간 안에 도착한 `BidResponse`만 경매 후보로 사용해야 한다.

RTB hot path에서는 네트워크 RTT, TCP/TLS connection setup, payload serialization, DSP 처리 시간, timeout 처리가 전체 latency에 영향을 준다.

이 프로젝트는 OpenRTB 기반 mini RTB 시스템이므로 SSP-DSP 경계의 표준 정합성을 유지해야 한다.

## Decision Drivers

- OpenRTB compatibility
- p95/p99 DSP call latency
- deadline compliance
- connection setup cost
- implementation complexity
- observability
- portfolio scope

## Options Considered

| Option | Compatibility | Latency Potential | Complexity | Decision |
|---|---:|---:|---:|---|
| HTTP/1.1 + JSON + persistent connection | High | Medium | Low | Baseline |
| HTTP/2 + JSON | High | Medium-High | Medium | Defer |
| gRPC + Protobuf | Low-Medium | High | High | Reject for baseline |
| HTTP/3 | Medium | Unclear | High | Defer |

## Analysis

DSP 호출 시간은 단순화하면 다음과 같다.

```text
T_dsp_call =
  T_connection_setup
+ T_request_transfer
+ T_dsp_processing
+ T_response_transfer
+ T_parse
```

새 connection을 만들면 `T_connection_setup`에 TCP handshake와 TLS handshake 비용이 포함된다.

persistent connection을 사용하면 hot path에서 이 비용을 제거할 수 있다.

전체 경매는 모든 DSP를 끝까지 기다리는 방식이 아니라, deadline 안에 도착한 응답만 사용한다.

```text
T_auction <= auction_deadline
```

따라서 초기 최적화의 핵심은 transport를 바꾸는 것보다 connection setup 비용을 제거하고, deadline 기반 fan-out을 구현하는 것이다.

## Decision

Baseline transport는 다음으로 정한다.

- HTTP POST
- JSON payload
- OpenRTB `BidRequest` / `BidResponse` subset
- persistent connection reuse
- deadline 기반 timeout

HTTP/2 + JSON은 OpenRTB HTTP semantics를 유지하므로 측정 후 고도화 후보로 둔다.

gRPC/Protobuf는 baseline에서 제외한다. 기술적으로 빠를 수 있지만 OpenRTB wire compatibility와 일반적인 SSP-DSP 연동 방식에서 멀어진다.

HTTP/3는 보류한다. 서버 간 RTB 호출에서 이점이 불확실하고 운영 복잡도가 크다.

## Consequences

Positive:

- OpenRTB 정합성을 유지한다.
- HTTP 기반 파트너 연동 모델과 맞다.
- TCP/TLS setup 비용을 hot path에서 줄일 수 있다.
- HTTP/2 실험 여지를 남긴다.

Negative:

- JSON serialization/deserialization 비용이 남는다.
- HTTP/2 multiplexing 이점은 baseline에서 사용하지 않는다.
- gRPC/Protobuf의 binary payload 이점은 포기한다.

## Revisit Conditions

다음 측정 결과 중 하나가 확인되면 재검토한다.

- DSP call p95/p99 latency가 timeout budget을 지속적으로 침범한다.
- Auction p95/p99 latency가 목표치를 넘는다.
- deadline compliance가 낮다.
- connection reuse ratio가 낮다.
- serialization/deserialization cost가 주요 병목으로 확인된다.
