# k6 Performance Tests

이 디렉토리는 경량 SSP와 경량 DSP의 hot path를 측정하기 위한 k6 스크립트를 둔다.

현재 `smoke.js`, `load-baseline.js`, `load-capacity.js`의 기본 입력 경로는 `POST /publisher/auction`이다. k6는 Provider Slot Request를 보내고, SSP가 내부에서 OpenRTB BidRequest를 생성한다. 기존 `POST /openrtb/auction` 직접 입력 경로는 `INGRESS_MODE=openrtb`를 지정할 때만 benchmark/호환성 비교용으로 사용한다.

## Test Types

| Type | Purpose | Current Status |
|---|---|---|
| Smoke | 메인 시나리오와 관찰 지표 파이프라인이 정상인지 확인 | `smoke.js` |
| HTTP OK baseline | 비즈니스 로직 없는 HTTP/JDK HttpServer 기준점 확인 | `http-ok-baseline.js` |
| OpenRTB JSON baseline | DSP fan-out 없는 JSON decode/encode 기준점 확인 | `openrtb-json-baseline.js` |
| Capacity | timeout DSP를 제외하고 순수 경매 처리량 한계 확인 | `load-capacity.js` |
| Timeout resilience | timeout DSP가 있을 때 deadline 격리 확인 | `load-baseline.js` |
| Stress | 부하를 올리며 시스템 한계와 병목 지점 확인 | `load-capacity.js` 사용 |
| Spike | 짧은 시간의 급격한 트래픽 증가 영향 확인 | 예정 |
| Soak | 장시간 부하에서 누수와 누적 악화 확인 | 후순위 |

## Smoke Test

Docker Compose 기반 성능 실험 환경을 먼저 실행한다.

```bash
docker compose -f docker-compose.perf.yml up --build -d ssp prometheus grafana
```

Compose topology:

| Service | Container Port | Host Port | Mode |
|---|---:|---:|---|
| `ssp` | `8080` | `8080` | auction endpoint |
| `dsp-a` | `8081` | `8081` | `normal-medium` |
| `dsp-b` | `8081` | `8082` | `normal-high` |
| `dsp-c` | `8081` | `8083` | `no-bid` |
| `dsp-d` | `8081` | `8084` | `timeout` |
| `prometheus` | `9090` | `9090` | metrics scrape |
| `grafana` | `3000` | `3000` | dashboard |

컨테이너에서 k6 smoke 실행:

```bash
docker compose -f docker-compose.perf.yml --profile test run --rm k6-smoke
```

로컬에 k6가 설치되어 있다면 host port를 통해 직접 실행할 수도 있다.

```bash
k6 run performance/k6/smoke.js
```

SSP 주소를 바꾸려면 다음 환경변수를 사용한다.

```bash
BASE_URL=http://localhost:8080 k6 run performance/k6/smoke.js
```

direct OpenRTB benchmark path를 쓰려면 다음처럼 실행한다.

```bash
INGRESS_MODE=openrtb BASE_URL=http://localhost:8080 k6 run performance/k6/smoke.js
```

검증 내용:

- HTTP status가 `200`인지
- AuctionResult가 `WINNER`인지
- 기본 topology에서 `dsp-b`가 낙찰되는지
- DSP 결과 분포가 `bid=2`, `no-bid=1`, `timeout=1`인지

이 smoke test는 성능 한계를 측정하지 않는다. k6 요청, SSP/DSP 연결, AuctionResult, Prometheus metric 생성이 함께 동작하는지 확인하는 첫 단계다.

정리:

```bash
docker compose -f docker-compose.perf.yml down
```

## Zero-base Baselines

전통적인 JSON over HTTP RTB baseline을 잡기 전에, 같은 EC2/Docker/JVM/JDK HttpServer 조건에서 더 얇은 기준점을 먼저 측정한다.

| Baseline | Endpoint | Excluded cost | Purpose |
|---|---|---|---|
| HTTP OK | `GET /ok` | JSON codec, OpenRTB object, auction, DSP call | VM + Docker + JVM + JDK HttpServer + HTTP/1.1의 순수 기준점 |
| OpenRTB JSON | `POST /baseline/openrtb-json` | auction, DSP fan-out, winner selection | OpenRTB JSON parse/serialize 비용 기준점 |

실행:

```bash
docker compose -f docker-compose.perf.yml up --build -d ssp prometheus
```

```bash
RPS=100 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-http-ok-baseline
RPS=100 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-openrtb-json-baseline
```

계단식 측정 예:

```bash
for rps in 100 300 500 1000 1500 2000; do
  RPS=$rps DURATION=1m PRE_ALLOCATED_VUS=$rps MAX_VUS=$((rps * 2)) \
    docker compose -f docker-compose.perf.yml --profile test run --rm k6-http-ok-baseline
done
```

```bash
for rps in 100 300 500 1000 1500 2000; do
  RPS=$rps DURATION=1m PRE_ALLOCATED_VUS=$rps MAX_VUS=$((rps * 2)) \
    docker compose -f docker-compose.perf.yml --profile test run --rm k6-openrtb-json-baseline
done
```

이 두 결과는 이후 실제 provider-facing `/publisher/auction` 결과와 비교한다. direct OpenRTB 입력만 분리해 보고 싶다면 `INGRESS_MODE=openrtb`로 `/openrtb/auction` 결과를 별도로 측정한다. 예를 들어 `/ok`와 JSON baseline은 안정적인데 실제 경매만 무너지면 VM이나 HTTP 서버 자체보다 slot request 검증, BidRequest 생성, SSP-DSP fan-out, executor, DSP 응답 처리, 도메인 로직 쪽을 먼저 의심한다.

## Load Capacity Baseline

Capacity baseline은 timeout DSP를 제외한 조건에서 시스템이 어느 정도의 요청률을 안정적으로 처리하는지 확인한다.

이 시나리오는 순수 처리량 측정용이다. 느린 DSP가 전체 경매에 미치는 영향은 `load-baseline.js`로 별도 측정한다.

Capacity topology:

| DSP | Mode |
|---|---|
| `dsp-a` | `normal-medium` |
| `dsp-b` | `normal-high` |
| `dsp-c` | `no-bid` |

감당 가능 기준:

- `checks == 100%`
- `http_req_failed == 0%`
- `winnerDspId == dsp-b`
- DSP 결과 분포가 `bid=2`, `no-bid=1`, `timeout=0`, `invalid=0`, `error=0`으로 유지
- p95/p99 latency를 기록

`load-capacity.js`는 아직 latency threshold로 실패하지 않는다. 이 단계의 목적은 목표 latency를 미리 가정하는 것이 아니라, 고정 조건에서 관찰된 p95/p99를 바탕으로 이후 목표 트래픽과 latency 기준을 정하는 것이다.

실행:

```bash
DSP_ENDPOINTS="dsp-a=http://dsp-a:8081/openrtb/bid,dsp-b=http://dsp-b:8081/openrtb/bid,dsp-c=http://dsp-c:8081/openrtb/bid" \
  docker compose -f docker-compose.perf.yml up --build -d ssp prometheus grafana
```

```bash
RPS=10 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-capacity
```

단계적으로 올려서 현재 구현의 기준을 찾는다.

```bash
RPS=10 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-capacity
RPS=30 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-capacity
RPS=50 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-capacity
```

VUs가 부족하면 k6가 요청을 충분히 만들지 못한다. 그 경우 다음 값을 함께 조정한다.

```bash
RPS=100 DURATION=2m PRE_ALLOCATED_VUS=150 MAX_VUS=300 \
  docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-capacity
```

이 결과는 production benchmark가 아니라 local reproducible baseline이다. 같은 머신에서 k6, SSP, DSP, Prometheus, Grafana가 함께 실행되므로 절대 처리량 보장으로 해석하지 않는다.

## Timeout Resilience Baseline

`load-baseline.js`는 `dsp-d=timeout`을 포함한 resilience 측정용 시나리오다.

검증 내용:

- DSP 결과 분포가 `bid=2`, `no-bid=1`, `timeout=1`, `invalid=0`, `error=0`으로 유지
- timeout DSP가 있어도 유효 bid 중 낙찰자를 반환
- timeout이 전체 경매 지연과 p95/p99에 미치는 영향 확인
