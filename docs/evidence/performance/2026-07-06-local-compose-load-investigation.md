# Local Compose Load Investigation - 2026-07-06

## 1. Context

Docker Compose 기반 로컬 성능 실험 환경에서 k6 load-baseline을 실행했다.

초기 목적은 다음 질문에 답하는 것이었다.

```text
고정된 로컬 Compose 조건에서 현재 구현은 어느 정도의 load를 감당하는가?
```

대상 시나리오는 단일 RTB hot path다.

```text
k6 -> SSP -> DSP-A/B/C/D -> SSP -> AuctionResult
```

## 2. Test Setup

Topology:

| Component | Location | Role |
|---|---|---|
| k6 | Docker container | Auction Client 역할 |
| SSP | Docker container | OpenRTB auction endpoint |
| DSP-A | Docker container | 정상 bid, 중간 가격 |
| DSP-B | Docker container | 정상 bid, 높은 가격 |
| DSP-C | Docker container | 정상 no-bid |
| DSP-D | Docker container | timeout |
| Prometheus | Docker container | SSP/DSP metrics scrape |
| Grafana | Docker container | metrics dashboard |

Request:

- media type: banner
- size: `300x250`
- bidfloor: `0.5 USD`
- `tmax`: `120ms`

## 3. Observation

k6 load-baseline result:

| RPS | Duration | Requests | Checks | Failed | k6 p95 | k6 p99 |
|---:|---:|---:|---:|---:|---:|---:|
| 5 | 10s | 50 | 100% | 0% | 127.45ms | 128.46ms |
| 10 | 20s | 201 | 100% | 0% | 5.01s | 12.89s |

Prometheus observation around the same investigation window:

| Metric | Observed value |
|---|---:|
| SSP auction duration p95 | about `132ms` |
| SSP auction duration p99 | about `134ms` |
| DSP-A call p95 | about `5ms` |
| DSP-B call p95 | about `5ms` |
| DSP-C call p95 | about `5ms` |
| DSP-D timeout p95 | about `132ms` |
| DSP internal bid handling p95 | about `1-2ms` |

DSP result distribution remained correct:

```text
dsp-a -> bid_received
dsp-b -> bid_received
dsp-c -> no_bid
dsp-d -> timeout
```

Auction result correctness also remained stable:

```text
status = WINNER
winnerDspId = dsp-b
```

## 4. Key Finding

k6 end-to-end latency and SSP internal auction duration diverged sharply.

```text
k6 p95 at 10 RPS: about 5.01s
SSP auction p95: about 132ms
```

This means the auction logic itself did not take 5 seconds.

The current metrics suggest that the delay happened before or around SSP HTTP handler execution, not inside the core auction flow.

Possible unmeasured area:

```text
k6 container
-> Docker network
-> SSP container port
-> JDK HttpServer accept / queue / executor
-> OpenRtbAuctionHttpHandler.handle()
```

## 5. Hypothesis

The latency spike is likely influenced by the local mixed test environment rather than the RTB auction logic itself.

Contributing factors:

- k6, SSP, DSPs, Prometheus, Grafana, Docker Desktop, and multiple JVMs share one local machine.
- k6 and the target system compete for the same CPU and memory resources.
- Docker Desktop on macOS adds virtualization and networking overhead.
- Each external auction request creates four internal DSP HTTP calls.
- DSP-D intentionally delays responses to simulate timeout behavior.
- The current metrics do not directly observe HTTP accept queue or executor wait time.

This is a hypothesis, not a final root cause.

## 6. Decision

Do not spend the next iteration deeply optimizing or instrumenting the local transport layer.

Reason:

- Transport-level behavior in the local Compose environment is not the core scope of the RTB portfolio project.
- Current Prometheus metrics show that SSP/DSP internal hot path latency is much lower than k6 end-to-end latency.
- Further local investigation may mostly explain Docker Desktop and local resource contention rather than RTB system behavior.

Instead, move to an externally isolated target-system test.

## 7. Next Action

Deploy the target system to an external VM and rerun the same k6 scenarios.

Target system:

```text
SSP + DSP-A/B/C/D
```

Load generator:

```text
Grafana Cloud k6 or local k6 from a separate machine
```

Monitoring:

```text
Start with local/VM Prometheus if needed.
Move to Grafana Cloud metrics later if the target deployment is stable.
```

Recommended cloud candidate:

```text
Oracle Cloud Always Free VM
```

Reason:

- It supports VM-based deployment.
- Docker Compose can run the SSP/DSP topology with minimal structural change.
- It is a better fit for five cooperating Java processes than serverless/container-edge platforms.

The next report should compare:

```text
Local Compose baseline
vs
External VM target-system baseline
```

The goal is not to claim production-grade throughput. The goal is to verify whether separating the load generator from the target system changes the latency profile.
