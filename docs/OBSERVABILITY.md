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
 -> Prometheus
 -> Grafana
```

Spring Boot Actuator is not used in the baseline. Micrometer is used directly so that Prometheus/Grafana integration remains possible while keeping the HTTP server stack small.

Cloud observability path for AWS performance tests:

```text
SSP / DSP on AWS EC2
 -> /metrics
 -> Prometheus on the same EC2
 -> remote_write
 -> Grafana Cloud Prometheus
 -> Grafana Cloud dashboard
```

Grafana Cloud is used as the external monitoring UI and metrics store. Prometheus remains close to the target system as the scraper so that SSP/DSP containers do not need to expose each metrics endpoint to the public internet.

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

성능 테스트의 재현성을 높이기 위해 SSP, DSP, Prometheus, Grafana, k6를 하나의 Compose topology로 실행할 수 있다.

```bash
docker compose -f docker-compose.perf.yml up --build -d ssp prometheus grafana
```

```bash
docker compose -f docker-compose.perf.yml --profile test run --rm k6-smoke
```

이 환경의 목적은 production benchmark가 아니라 local baseline measurement다. 모든 컨테이너가 같은 로컬 머신 자원을 공유하므로 절대 성능 보장으로 해석하지 않는다.

Compose 환경에서는 Prometheus가 Docker network 내부 service name으로 scrape한다.

| Target | Purpose |
|---|---|
| `ssp:8080/metrics` | SSP auction metrics |
| `dsp-a:8081/metrics` | DSP-A metrics |
| `dsp-b:8081/metrics` | DSP-B metrics |
| `dsp-c:8081/metrics` | DSP-C metrics |
| `dsp-d:8081/metrics` | DSP-D metrics |

## 9. Grafana Cloud Monitoring

AWS 성능 측정에서는 Grafana UI를 EC2에 함께 띄우지 않고 Grafana Cloud를 사용한다.

Repository에는 비밀값이 없는 템플릿만 둔다.

```text
monitoring/prometheus/prometheus.cloud.yml.template
docker-compose.cloud-monitoring.yml
```

실제 배포 파일은 Git에 커밋하지 않는다.

```bash
cp monitoring/prometheus/prometheus.cloud.yml.template monitoring/prometheus/prometheus.cloud.yml
mkdir -p .secrets
printf '%s' '<Grafana Cloud Access Policy Token>' > .secrets/grafana_cloud_api_token
chmod 600 .secrets/grafana_cloud_api_token
```

Cloud monitoring compose override:

```bash
docker compose \
  -f docker-compose.perf.yml \
  -f docker-compose.cloud-monitoring.yml \
  up --build -d ssp prometheus
```

Grafana Cloud로 전송되는 metric에는 다음 external labels를 붙인다.

| Label | Value |
|---|---|
| `project` | `low-latency-openrtb-auction` |
| `environment` | `aws-perf` |
| `service_group` | `rtb` |

Grafana Cloud에서 이 프로젝트 metric만 확인할 때는 다음 label filter를 사용한다.

```promql
{project="low-latency-openrtb-auction", environment="aws-perf"}
```
