# Performance Baseline

이 문서는 Docker Compose 기반 로컬 성능 실험 환경에서 관찰한 load capacity baseline을 기록한다.

이 결과는 production benchmark가 아니다. k6, SSP, DSP, Prometheus, Grafana가 같은 로컬 머신 자원을 공유하므로 절대 처리량 보장으로 해석하지 않는다.

## 1. Test Environment

Date: 2026-07-05

Topology:

```text
k6 -> SSP container -> DSP-A/B/C/D containers -> SSP -> k6
Prometheus -> SSP/DSP /metrics
Grafana -> Prometheus
```

Services:

| Service | Role |
|---|---|
| `ssp` | Auction endpoint |
| `dsp-a` | 정상 bid, 중간 가격 |
| `dsp-b` | 정상 bid, 높은 가격 |
| `dsp-c` | 정상 no-bid |
| `dsp-d` | timeout |

Request:

- media type: banner
- size: `300x250`
- bidfloor: `0.5 USD`
- auction type: first price
- `tmax`: `120ms`

## 2. Capacity Criteria

Load를 감당한다고 판단하려면 다음 조건을 유지해야 한다.

| Category | Criteria |
|---|---|
| HTTP stability | `http_req_failed == 0%` |
| Auction correctness | k6 checks `100%` |
| Winner | `winnerDspId == dsp-b` |
| DSP result distribution | `bid=2`, `no-bid=1`, `timeout=1`, `invalid=0`, `error=0` |
| Latency | p95/p99를 기록하고 목표 기준은 baseline 이후 정의 |

## 3. Initial Load Baseline

Commands:

```bash
RPS=5 DURATION=10s PRE_ALLOCATED_VUS=10 MAX_VUS=30 \
  docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-baseline
```

```bash
RPS=10 DURATION=20s PRE_ALLOCATED_VUS=100 MAX_VUS=200 \
  docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-baseline
```

Observed results:

| RPS | Duration | Requests | Checks | Failed | p95 | p99 | Interpretation |
|---:|---:|---:|---:|---:|---:|---:|---|
| 5 | 10s | 50 | 100% | 0% | 127.45ms | 128.46ms | stable |
| 10 | 20s | 201 | 100% | 0% | 5.01s | 12.89s | functionally stable, latency degraded |

## 4. Initial Interpretation

The current implementation preserves auction correctness at 10 RPS, but latency degrades sharply between 5 RPS and 10 RPS in the local Compose environment.

This means the next performance work should not jump directly to higher RPS. The next step is to identify the hot-path bottleneck before defining an ambitious target traffic number.

Likely investigation points:

- JDK `HttpServer` request handling executor behavior
- SSP request concurrency
- DSP fan-out waiting behavior
- timeout DSP impact on request worker occupancy
- Java `HttpClient` connection usage

## 5. Next Baseline Steps

Recommended next measurements:

```text
1 RPS -> 3 RPS -> 5 RPS -> 7 RPS -> 10 RPS
```

For each step, record:

- k6 p95/p99
- k6 checks
- k6 observed throughput
- SSP auction p95/p99 from Prometheus
- SSP -> DSP call p95/p99 from Prometheus
- DSP result distribution

After this narrower sweep, define the first local target traffic and latency goal.
