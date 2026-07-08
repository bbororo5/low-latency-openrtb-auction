# Observability Strategy

이 문서는 RTB hot path의 지연과 결과 품질을 설명하기 위해 어떤 메트릭을 수집할지 정의한다.

목표는 운영 모니터링 도구를 많이 붙이는 것이 아니라, 성능 측정과 최적화 과정에서 다음 질문에 답할 수 있는 최소 관찰성을 확보하는 것이다.

- 전체 경매가 느린가?
- DSP 호출이 느린가?
- DSP 내부 입찰 판단이 느린가?
- timeout, no-bid, no-winner가 왜 발생했는가?
- 성능 개선이 결과 품질을 망치지 않았는가?

## 1. Observability Principles

메트릭은 운영 질문에 답하는 숫자다. 이 프로젝트는 SRE의 Four Golden Signals 중 1차로 Latency, Traffic, Errors에 집중한다.

| Signal | 질문 | 이 프로젝트의 의미 |
|---|---|---|
| Latency | 얼마나 오래 걸리는가? | auction duration, DSP call duration, DSP bid handling duration |
| Traffic | 얼마나 많이 처리하는가? | auction request count, DSP call count, bid handling count |
| Errors | 얼마나 실패하거나 제외되는가? | timeout, late bid, invalid bid, error |
| Saturation | 자원이 얼마나 찼는가? | active threads, connection usage, CPU, GC. 초기 범위에서는 2차로 둔다. |

RTB에서는 모든 비정상처럼 보이는 결과가 장애는 아니다. `NO_BID`는 정상적인 비입찰이고, `NO_WINNER`도 유효 bid가 없는 정상 결과일 수 있다. 따라서 timeout, invalid bid, no-bid, no-winner를 하나의 error로 묶지 않는다.

## 2. Primary Metrics

1차 메트릭은 전체 경로와 주요 경계만 본다.

### SSP

| Metric | Type | Tags | Purpose |
|---|---|---|---|
| `rtb_ssp_auction_duration` | Timer | `media_type`, `result` | Auction Client 요청부터 AuctionResult 반환까지의 지연 시간 |
| `rtb_ssp_auction_result_total` | Counter | `media_type`, `result` | winner, no-winner, invalid request, unsupported request 분포 |
| `rtb_ssp_dsp_call_duration` | Timer | `dsp_id`, `result` | SSP가 DSP 하나를 호출하고 결과를 분류하기까지의 지연 시간 |
| `rtb_ssp_dsp_call_result_total` | Counter | `dsp_id`, `result` | DSP별 bid, no-bid, timeout, late bid, error 분포 |

### DSP

| Metric | Type | Tags | Purpose |
|---|---|---|---|
| `rtb_dsp_bid_handle_duration` | Timer | `media_type`, `result` | DSP가 BidRequest를 받아 bid/no-bid를 결정하기까지의 지연 시간 |
| `rtb_dsp_bid_result_total` | Counter | `media_type`, `result` | DSP의 bid, no-bid, error 분포 |
| `rtb_dsp_no_bid_reason_total` | Counter | `media_type`, `reason` | DSP가 no-bid를 낸 이유 분포 |

## 3. Secondary Metrics

2차 메트릭은 1차 메트릭에서 병목이 의심될 때 추가한다.

| Metric | Purpose |
|---|---|
| `rtb_dsp_campaign_lookup_duration` | 캠페인 후보 조회가 p95/p99에 미치는 영향 확인 |
| `rtb_dsp_matcher_duration` | 타겟 조건 매칭 비용 확인 |
| `rtb_dsp_pricing_duration` | 가격 산정 비용 확인 |
| `rtb_ssp_dsp_executor_active_threads` | SSP가 DSP HTTP 호출에 실제로 사용 중인 worker 수 확인 |
| `rtb_ssp_dsp_executor_pool_size` | SSP DSP HTTP executor가 어느 정도까지 커졌는지 확인 |
| `rtb_ssp_dsp_executor_queued_tasks` | DSP 호출 작업이 executor queue에 밀리는지 확인 |
| `rtb_ssp_dsp_executor_rejected_tasks` | executor 한계를 넘어선 요청이 발생했는지 확인 |
| JVM / GC / thread / connection metrics | 자원 포화 또는 runtime 병목 의심 시 확인 |

## 4. Tag Policy

Prometheus에서는 label, Micrometer에서는 tag라고 부른다. tag는 metric을 나누어 보는 차원이다.

낮은 cardinality tag만 허용한다.

Allowed:

- `app`: `ssp`, `dsp`
- `media_type`: `banner`, `video`, `native`, `unknown`
- `result`: `winner`, `no_winner`, `bid`, `no_bid`, `timeout`, `late_bid`, `invalid_bid`, `error`
- `reason`: `no_campaign`, `no_matched_campaign`, `bid_below_floor`, `missing_creative`, `invalid_request`, `unsupported_request`
- `dsp_id`: 테스트에서 사용하는 고정된 경량 DSP 식별자

Disallowed:

- `request_id`
- `imp_id`
- `bid_id`
- `campaign_id`
- `creative_id`
- `user_id`
- `ip`

높은 cardinality 값은 metric tag가 아니라 log에 남긴다.

## 5. Metrics and Logs

메트릭은 집계용이고, 로그는 개별 사건 추적용이다.

Metric examples:

- timeout rate
- no-bid reason count
- auction p95/p99

Log examples:

- `requestId`
- `impId`
- `dspId`
- `campaignId`
- `elapsedMs`
- `reason`

## 6. Monitoring Path

Baseline observability path:

```text
SSP / DSP
 -> JDK HttpServer
 -> Micrometer Timer/Counter
 -> /metrics
 -> Grafana Cloud Metrics Endpoint
 -> Grafana Cloud Prometheus-compatible metrics
 -> Grafana Cloud dashboard
```

Spring Boot Actuator is not used in the baseline. Micrometer is used directly so that Prometheus/Grafana integration remains possible while keeping the HTTP server stack small.

APM observability path:

```text
SSP / DSP
 -> OpenTelemetry Java Agent
 -> OTLP HTTP
 -> Grafana Alloy
 -> Grafana Cloud OTLP endpoint
 -> Grafana Cloud Application Observability
```

APM은 기존 도메인 메트릭을 대체하지 않는다. `/metrics`는 경매 결과 품질, timeout/no-bid 분포, DSP별 지연을 집계하는 경로로 유지하고, OpenTelemetry는 요청 단위 trace와 서비스 간 호출 흐름을 확인하는 경로로 사용한다.

Cloud observability path for AWS performance tests:

```text
SSP / DSP on AWS EC2
 -> Caddy HTTPS endpoint
 -> /metrics/*
 -> Grafana Cloud Metrics Endpoint
 -> Grafana Cloud Prometheus
 -> Grafana Cloud dashboard
```

Grafana Cloud is used as the scraper, metrics store, and dashboard UI. EC2 runs the target system containers and a small HTTPS reverse proxy.

The trade-off is that metrics endpoints must be reachable from Grafana Cloud over HTTPS. Caddy provides the HTTPS endpoint and routes public metrics paths to internal SSP/DSP containers.

## 7. Local Monitoring Setup

SSP와 DSP 애플리케이션은 로컬 JVM 프로세스로 실행하고, Prometheus/Grafana만 Docker Compose로 실행한다.

```bash
docker compose -f docker-compose.observability.yml up
```

Prometheus scrape targets:

| Target | Purpose |
|---|---|
| `host.docker.internal:8080/metrics` | SSP auction metrics |
| `host.docker.internal:8081/metrics` | DSP-A metrics |
| `host.docker.internal:8082/metrics` | DSP-B metrics |
| `host.docker.internal:8083/metrics` | DSP-C metrics |
| `host.docker.internal:8084/metrics` | DSP-D metrics |

Grafana는 `http://localhost:3000`, Prometheus는 `http://localhost:9090`에서 확인한다.

Latency timer는 Prometheus histogram bucket을 함께 노출한다. p95/p99는 Grafana에서 다음 형태로 확인한다.

```promql
histogram_quantile(0.95, rate(rtb_ssp_auction_duration_seconds_bucket[1m]))
```

```promql
histogram_quantile(0.99, rate(rtb_ssp_dsp_call_duration_seconds_bucket[1m]))
```

## 8. Docker Compose Performance Environment

성능 테스트 대상 시스템은 SSP, DSP, Caddy 컨테이너만 실행한다.

```bash
docker compose -f docker-compose.perf.yml up --build -d caddy
```

`caddy`는 `ssp`, `dsp-a`, `dsp-b`, `dsp-c`, `dsp-d`에 의존하므로 Compose가 target system 컨테이너도 함께 실행한다.

```bash
docker compose -f docker-compose.perf.yml --profile test run --rm k6-smoke
```

로컬 `k6-smoke`는 기능 확인용이다. 성능 측정용 부하 발생기는 Grafana Cloud k6를 사용한다.

OpenTelemetry APM을 함께 켤 때는 Grafana Cloud OTLP credential을 준비한다.

```bash
cp .env.grafana-cloud.example .env.grafana-cloud
```

`.env.grafana-cloud`에는 Grafana Cloud OpenTelemetry connection tile에서 받은 값을 넣는다. 이 파일은 Git에 커밋하지 않는다.

```bash
docker compose --env-file .env.grafana-cloud -f docker-compose.perf.yml -f docker-compose.otel.yml up --build -d caddy alloy
```

이 override는 `JAVA_TOOL_OPTIONS=-javaagent:/otel/opentelemetry-javaagent.jar`로 Java Agent를 켠다. 기본 `docker-compose.perf.yml`만 실행하면 agent는 활성화되지 않는다.

AWS target system의 public metrics endpoints:

| Target | Purpose |
|---|---|
| `https://13-125-82-244.sslip.io/metrics/ssp` | SSP auction metrics |
| `https://13-125-82-244.sslip.io/metrics/dsp-a` | DSP-A metrics |
| `https://13-125-82-244.sslip.io/metrics/dsp-b` | DSP-B metrics |
| `https://13-125-82-244.sslip.io/metrics/dsp-c` | DSP-C metrics |
| `https://13-125-82-244.sslip.io/metrics/dsp-d` | DSP-D metrics |

## 9. Grafana Cloud Metrics Monitoring

AWS 성능 측정에서는 EC2에 Grafana와 Prometheus를 함께 띄우지 않는다.

Grafana Cloud Metrics Endpoint에 scrape job을 등록한다.

| Job | URL |
|---|---|
| `rtb-ssp` | `https://13-125-82-244.sslip.io/metrics/ssp` |
| `rtb-dsp-a` | `https://13-125-82-244.sslip.io/metrics/dsp-a` |
| `rtb-dsp-b` | `https://13-125-82-244.sslip.io/metrics/dsp-b` |
| `rtb-dsp-c` | `https://13-125-82-244.sslip.io/metrics/dsp-c` |
| `rtb-dsp-d` | `https://13-125-82-244.sslip.io/metrics/dsp-d` |

각 scrape job에는 다음 labels를 붙인다.

| Label | Value |
|---|---|
| `project` | `low-latency-openrtb-auction` |
| `environment` | `aws-perf` |
| `service_group` | `rtb` |

DSP job에는 추가로 `app=dsp`, `dsp_id=dsp-a`처럼 DSP 식별 label을 붙인다. SSP job에는 `app=ssp`를 붙인다.

Metrics Endpoint authentication:

| Field | Value |
|---|---|
| Authentication type | `Basic` |
| Username | `grafana` |
| Password | Stored outside Git in `.secrets/grafana_metrics_endpoint_password` |

Grafana Cloud에서 이 프로젝트 metric만 확인할 때는 다음 label filter를 사용한다.

```promql
{project="low-latency-openrtb-auction", environment="aws-perf"}
```

현재 Grafana Cloud Metrics Endpoint는 `scrape_job` label을 기준으로도 조회할 수 있다.
대시보드 import JSON은 현재 수집 label에 맞춰 `scrape_job`을 사용한다.

| Dashboard | File | Purpose |
|---|---|---|
| RTB Auction Overview | `monitoring/grafana/cloud/rtb-auction-overview.import.json` | 경매 결과, SSP/DSP 지연, DSP 결과 분포 확인 |
| RTB Runtime Saturation | `monitoring/grafana/cloud/rtb-runtime-saturation.import.json` | JVM thread, CPU, heap, GC, SSP in-flight DSP call, SSP DSP executor 상태 확인 |

두 대시보드는 역할이 다르다.

```text
Auction Overview: 경매가 깨졌는가?
Runtime Saturation: 왜 깨졌는가?
```

SSP DSP executor는 SSP가 여러 DSP로 HTTP 요청을 보낼 때 사용하는 제한된 worker pool이다. 이 값은 전체 JVM thread 수가 튀었을 때 원인이 DSP fan-out인지, executor queue 포화인지, 아니면 다른 runtime 문제인지 구분하기 위해 본다.

## 10. Grafana Cloud APM

OpenTelemetry APM은 다음 opt-in Compose override로 켠다.

```bash
docker compose --env-file .env.grafana-cloud -f docker-compose.perf.yml -f docker-compose.otel.yml up --build -d caddy alloy
```

수집 경로:

```text
SSP / DSP containers
 -> OpenTelemetry Java Agent
 -> alloy:4318
 -> Grafana Alloy batch processor
 -> Grafana Cloud OTLP endpoint
```

Grafana Cloud Application Observability에서 다음을 확인한다.

| Service | Expected role |
|---|---|
| `rtb-ssp` | `/openrtb/auction` inbound request와 DSP fan-out outbound HTTP call |
| `rtb-dsp-a` | 정상 medium bid DSP inbound request |
| `rtb-dsp-b` | 정상 high bid DSP inbound request |
| `rtb-dsp-c` | no-bid DSP inbound request |
| `rtb-dsp-d` | timeout mode DSP inbound request |

Smoke trace가 잘 보이지 않으면 검증 중에만 `.env.grafana-cloud`에서 sampling ratio를 올린다.

```bash
OTEL_TRACES_SAMPLER_ARG=1.0
```

검증 후에는 load test overhead와 ingestion 비용을 줄이기 위해 다시 기본값으로 돌린다.

```bash
OTEL_TRACES_SAMPLER_ARG=0.10
```

면접 방어 포인트:

- Java Agent를 선택한 이유: 애플리케이션 코드 변경 없이 JDK HttpServer, Java HTTP Client, JVM runtime telemetry를 자동 수집할 수 있다.
- Alloy를 선택한 이유: 애플리케이션이 Grafana Cloud로 직접 전송하지 않고, batch/retry/credential boundary를 collector 계층에 둔다.
- Prometheus 메트릭을 유지하는 이유: RTB 도메인 질문인 auction result, timeout, no-bid reason, DSP별 latency는 명시적 Micrometer metric이 더 설명력이 높다.
- Sampling을 두는 이유: RTB hot path에서는 모든 요청 trace를 저장하면 지연 overhead와 저장 비용이 커질 수 있으므로 기본 10%로 시작하고 검증 때만 100%로 올린다.

## 11. Grafana Cloud k6

성능 측정용 부하 발생기는 Grafana Cloud k6를 사용한다.

```text
Grafana Cloud k6
 -> https://13-125-82-244.sslip.io/openrtb/auction
 -> AWS EC2 SSP
 -> DSP-A/B/C/D
```

이 구조는 local Mac 또는 Docker Desktop 네트워크를 부하 테스트 경로에서 제거한다. 결과를 해석할 때는 Grafana Cloud k6의 load zone과 AWS region을 함께 기록한다.

## 12. Prometheus Remote Write Alternative

EC2 내부 scrape가 필요해지면 Prometheus remote_write 구성을 다시 사용할 수 있다.

```text
SSP / DSP
 -> Prometheus on EC2
 -> Grafana Cloud remote_write
```

이 방식은 `/metrics`를 public으로 열지 않아도 되는 장점이 있지만, EC2에 Prometheus 컨테이너를 남긴다. 현재 AWS performance setup에서는 사용하지 않는다.
