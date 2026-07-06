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

Prometheus metrics showed that SSP internal auction duration stayed around `132ms` while k6 end-to-end p95 reached seconds. This suggests that the sharp latency increase is likely outside the core RTB auction logic, around the local Docker/k6/HTTP server boundary.

Detailed investigation note:

- [Local Compose Load Investigation - 2026-07-06](performance/2026-07-06-local-compose-load-investigation.md)

This means the next performance work should not jump directly to higher RPS or local HTTP server tuning. The next step is to separate the target system from the load generator and rerun the same scenario.

Likely investigation points:

- JDK `HttpServer` request handling executor behavior
- SSP request concurrency
- DSP fan-out waiting behavior
- timeout DSP impact on request worker occupancy
- Java `HttpClient` connection usage

## 5. External Target Baseline

로컬 Compose 환경에서는 k6, SSP, DSP, Prometheus, Grafana가 같은 컴퓨터 자원을 공유했다.
따라서 다음 단계로 AWS 서버 1대에 대상 시스템을 배포하고, 내 컴퓨터에서 k6 요청을 보내는 방식으로 다시 측정했다.

이 기준선은 운영 환경의 성능 보장이 아니다.
현재 서버 1대에서 안정적으로 반복 가능한 비교 출발점을 만드는 것이 목적이다.

### Environment

| Item | Value |
|---|---|
| Target server | AWS EC2 1대 |
| Instance type | `m7i-flex.large` |
| Server resources | 2 vCPU, 8 GiB memory |
| Region | `ap-northeast-2` |
| Target topology | SSP 1개 + DSP 4개 |
| Load generator | local k6 container |

Request:

- media type: banner
- size: `300x250`
- bidfloor: `0.5 USD`
- auction type: first price
- `tmax`: `120ms`

DSP behavior:

| DSP | Behavior |
|---|---|
| `dsp-a` | 정상 bid, 중간 가격 |
| `dsp-b` | 정상 bid, 높은 가격 |
| `dsp-c` | 정상 no-bid |
| `dsp-d` | timeout |

### Baseline Criteria

현재 기준선은 `100 RPS / 30s`로 정한다.

| Item | Criteria |
|---|---:|
| Request rate | `100 RPS` |
| Duration | `30s` |
| HTTP failure rate | `0%` |
| k6 checks | `100%` |
| p95 latency | `<= 200ms` |
| p99 latency | `<= 250ms` |
| dropped iterations | `0` |
| Winner | `winnerDspId == dsp-b` |
| DSP result distribution | `bid=2`, `no-bid=1`, `timeout=1`, `invalid=0`, `error=0` |

p95는 대부분의 요청이 어느 정도 시간 안에 끝나는지 보는 값이다.
p99는 거의 모든 요청이 어느 정도 시간 안에 끝나는지 보는 값이다.

### Load Step Results

JDK HttpServer executor 설정 후 AWS 대상 시스템에서 부하를 단계적으로 올려 측정했다.

| RPS | Duration | Requests | Checks | Failed | p95 | p99 | Dropped | Result |
|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 10 | 30s | 301 | 100% | 0% | 171.74ms | 203.55ms | 0 | stable |
| 20 | 30s | 601 | 100% | 0% | 153.76ms | 186.50ms | 0 | stable |
| 30 | 30s | 901 | 100% | 0% | 160.52ms | 321.71ms | 0 | stable with p99 spike |
| 50 | 30s | 1,500 | 100% | 0% | 154.59ms | 204.09ms | 0 | stable |
| 100 | 30s | 3,001 | 100% | 0% | 152.71ms | 180.79ms | 0 | stable |

Prometheus 기준 SSP 내부 경매 시간은 다음과 같다.

| Metric | Observed |
|---|---:|
| SSP auction winner p50 | `123.1ms` |
| SSP auction winner p95 | `133.2ms` |
| SSP auction winner p99 | `134.1ms` |

정상 bid를 반환하는 DSP 호출은 p95 기준 약 `16ms` 안쪽이었다.
의도적으로 timeout되는 `dsp-d`는 경매 deadline 근처에서 timeout 처리되었다.

### Stress Boundary Observation

`100 RPS` 이후에는 한계 지점을 찾기 위해 `150 RPS`, `200 RPS`를 실행했다.

| RPS | Duration | Requests | Checks | Failed | p95 | p99 | Main symptom |
|---:|---:|---:|---:|---:|---:|---:|---|
| 150 | 30s | 4,501 | 99.93% | 0.06% | 202.45ms | 330.50ms | `dial: i/o timeout` 3건 |
| 200 | 30s | 6,000 | 99.10% | 0.89% | 153.67ms | 180.31ms | `dial: i/o timeout` 54건 |

`150 RPS`부터는 경매 결과가 틀어진 것이 아니라, k6가 SSP에 연결을 여는 단계에서 `dial: i/o timeout`을 기록했다.
따라서 이 구간은 경매 로직 자체의 실패라기보다 외부 연결, 네트워크, 서버 accept 계층의 한계 후보로 본다.

### Interpretation

이 기준선은 다음 상태를 의미한다.

- AWS 서버 1대에서 SSP 1개와 DSP 4개를 실행한다.
- 내 컴퓨터에서 초당 100개 요청을 30초 동안 보낸다.
- 모든 요청은 정상 경매 결과를 반환한다.
- p95는 `200ms` 이하, p99는 `250ms` 이하로 유지된다.
- 이후 성능 변경은 이 기준선과 비교한다.
- `150 RPS`부터는 연결 timeout이 발생하므로 안정 기준선으로 보지 않는다.

상세 조사 기록:

- [AWS HttpServer Executor Investigation - 2026-07-06](performance/2026-07-06-aws-httpserver-executor-investigation.md)

## 6. Next Measurement Steps

다음 단계는 `150 RPS` 이상에서 발생한 연결 timeout의 원인을 분리하는 것이다.

Recommended investigation points:

```text
client-side k6 connection behavior
EC2 network/socket queue
JDK HttpServer accept behavior
host-level CPU and network saturation
```

For each step, record:

- k6 p95/p99
- k6 checks
- k6 observed throughput
- dropped iterations
- connection timeout count
- SSP auction p95/p99 from Prometheus
- SSP -> DSP call p95/p99 from Prometheus
- DSP result distribution

그 다음 최적화 주제는 결과를 보고 정한다.
후보는 DSP 수 증가, deadline 조건 변화, campaign matching 비용, connection reuse 여부다.
