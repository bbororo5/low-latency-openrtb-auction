# Performance Baseline

이 문서는 성능 실험 환경을 단계적으로 분리하며 관찰한 load capacity baseline을 기록한다.

이 결과는 production benchmark가 아니다. 각 측정값은 해당 부하 발생기, 네트워크 경로, target system 배포 조건 안에서만 해석한다.

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

## 3. Baseline Layers

전통적인 JSON over HTTP 방식을 현재 기준선으로 둔다. Protobuf는 이후 최적화 스토리에서 비교할 후보로 남기고, 먼저 JSON 경로를 계층별로 분리 측정한다.

| Layer | Endpoint or path | Measures | Excludes |
|---|---|---|---|
| Host baseline | `scripts/performance/aws-vm-baseline.sh` | EC2 CPU, memory, disk, kernel/socket/thread limits | JVM, Docker application path |
| HTTP OK baseline | `GET /ok` | EC2 + Docker + JVM + JDK HttpServer + HTTP/1.1 | JSON codec, OpenRTB object, auction, DSP call |
| OpenRTB JSON baseline | `POST /baseline/openrtb-json` | OpenRTB JSON decode/encode cost | auction flow, DSP fan-out, winner selection |
| RTB JSON capacity | `POST /openrtb/auction` with `dsp-a/b/c` | actual JSON over HTTP RTB hot path | timeout DSP |
| RTB JSON timeout resilience | `POST /openrtb/auction` with `dsp-a/b/c/d` | deadline isolation with slow DSP | pure throughput measurement |

Measurement order:

```text
host baseline
 -> /ok RPS and p95/p99
 -> /baseline/openrtb-json RPS and p95/p99
 -> /openrtb/auction JSON capacity
 -> /openrtb/auction JSON timeout resilience
```

This separates infrastructure ceiling, HTTP server cost, JSON serialization cost, and SSP-DSP fan-out cost before introducing a Protobuf optimization variant.

## 4. Initial Load Baseline

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

## 5. Initial Interpretation

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

## 6. External Target Baseline

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

추가 확인을 위해 k6를 AWS 내부 Compose network에서 실행했다.
이 경우 public internet과 local Mac Docker 경로가 제거된다.

| Load generator path | RPS | Duration | Requests | Checks | Failed | p95 | p99 |
|---|---:|---:|---:|---:|---:|---:|---:|
| AWS internal Docker network | 150 | 30s | 4,500 | 100% | 0% | 120.12ms | 121.76ms |
| AWS internal Docker network | 200 | 30s | 6,001 | 100% | 0% | 120.05ms | 121.07ms |

따라서 현재 증거만으로는 target system 자체가 `150 RPS`에서 실패했다고 볼 수 없다.
외부 테스트의 실패는 local Mac Docker, public network, client connection 생성 경로의 영향일 가능성이 높다.

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
- [Load Generator Path Investigation - 2026-07-06](performance/2026-07-06-load-generator-path-investigation.md)

## 7. Next Measurement Steps

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

## 8. Capacity Scenario Without Timeout DSP

기존 baseline은 매 요청마다 `dsp-d=timeout`을 포함했다.
이 구성은 timeout 격리 검증에는 유효하지만, 순수 처리량 한계를 찾기에는 부적절했다.

따라서 timeout DSP를 제외한 capacity 시나리오를 분리했다.

```text
Capacity: dsp-a normal bid, dsp-b high bid, dsp-c no-bid
Expected: bid=2, no-bid=1, timeout=0
```

Grafana Cloud k6 100 VU 조건에서 관찰한 경계:

| RPS | Result | Interpretation |
|---:|---|---|
| 150 | warning 없이 성공 | stable candidate |
| 165 | warning 없이 성공 | highest clean run observed |
| 180 | VU shortage 경고 | load generator boundary 후보 |
| 200 | VU shortage 경고 | stable로 보지 않음 |
| 500 | VU shortage, request timeout, DNS error | target 한계로 단정 불가 |

현재 결론:

```text
timeout 없는 capacity scenario에서는 165 RPS까지 경고 없이 실행되었다.
180 RPS부터는 target failure보다 Grafana Cloud k6 100 VU 제한이 먼저 관찰되었다.
```

상세 기록:

- [Capacity Scenario Stress - 2026-07-06](performance/2026-07-06-capacity-scenario-stress.md)

## 9. Cloud Measurement Path

Grafana Cloud k6와 Grafana Cloud Metrics Endpoint는 외부 관찰 경로로 사용한다.

```text
Grafana Cloud k6
 -> AWS EC2 Caddy HTTPS
 -> SSP
 -> DSP-A/B/C/D
```

```text
Grafana Cloud Metrics Endpoint
 -> AWS EC2 Caddy HTTPS /metrics/*
 -> SSP/DSP metrics endpoints
```

EC2에는 target system과 HTTPS reverse proxy만 남긴다.

| Component | Current role |
|---|---|
| Grafana Cloud k6 | 부하 발생기 |
| Grafana Cloud Metrics Endpoint | metrics scraper |
| Grafana Cloud metrics storage | Prometheus-compatible metrics store |
| AWS EC2 | SSP/DSP target system |
| Caddy | HTTPS endpoint and metrics Basic Auth |

Initial verification:

| Test | Run URL | Status |
|---|---|---|
| Cloud smoke | `https://curiouscicada2096.grafana.net/a/k6-app/runs/8041428` | `Finished` |
| Cloud 10 RPS baseline | `https://curiouscicada2096.grafana.net/a/k6-app/runs/8041454` | `Finished` |

Metrics Endpoint status:

```promql
up{scrape_job=~"rtb-.*"}
```

All five jobs are `up=1`:

- `rtb-ssp`
- `rtb-dsp-a`
- `rtb-dsp-b`
- `rtb-dsp-c`
- `rtb-dsp-d`

Detailed note:

- [Grafana Cloud k6 Baseline - 2026-07-06](performance/2026-07-06-grafana-cloud-k6-baseline.md)

Cloud k6 run pages remain useful for remote smoke checks and external-path observations.
They are not used as the primary source for high-RPS capacity claims.

이후 높은 RPS의 capacity 한계 측정에서는 Grafana Cloud k6를 기준 경로로 사용하지 않는다.
이유는 다음과 같다.

- load zone이 target AWS region과 달라 network path가 길다.
- 현재 프로젝트 조건에서는 VU 제한이 먼저 관찰된다.
- DNS, public HTTPS, remote load zone이 섞여 target system 한계와 분리하기 어렵다.

## 10. AWS Load Generator Capacity Baseline

높은 RPS의 capacity 측정은 target과 같은 AWS region의 별도 loadgen EC2에서 수행한다.

```text
AWS loadgen EC2
 -> target EC2 private IP:8080
 -> SSP
 -> DSP-A/B/C
```

이 경로는 local Mac, Docker Desktop, public internet, Grafana Cloud k6 VU 제한을 제외한다.

Current capacity scenario:

```text
Capacity: dsp-a normal bid, dsp-b high bid, dsp-c no-bid
Expected: bid=2, no-bid=1, timeout=0
```

Observed boundary:

| RPS | Duration | Result | Interpretation |
|---:|---:|---|---|
| 800 | 1m | checks 100%, HTTP failure 0% | stable |
| 900 | 1m | checks 100%, HTTP failure 0% | stable |
| 1000 retry | 1m | checks 100%, HTTP failure 0% | current stable baseline |
| 1100 | 1m | checks 37.50%, HTTP failure 0% | failed fast with invalid auction result |
| 1200 | 1m | checks 37.74%, dropped iterations 19,843 | failed |
| 1500 | 1m | checks 38.18%, dropped iterations 18,049 | failed |

Important finding:

```text
1100 RPS 이상에서 SSP 로그에 pthread_create failed (EAGAIN)이 반복되었다.
즉, 다음 병목 후보는 단순 CPU가 아니라 native thread 생성/관리 비용이다.
```

현재 기준선은 다음과 같이 둔다.

```text
1000 RPS / 1m
checks 100%
HTTP failure 0%
dropped iterations 0
expected winner = dsp-b
```

상세 기록:

- [AWS Load Generator Capacity - 2026-07-07](performance/2026-07-07-aws-loadgen-capacity.md)
