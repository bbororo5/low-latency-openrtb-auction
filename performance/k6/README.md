# k6 Performance Tests

이 디렉토리는 경량 SSP와 경량 DSP의 hot path를 측정하기 위한 k6 스크립트를 둔다.

## Test Types

| Type | Purpose | Current Status |
|---|---|---|
| Smoke | 메인 시나리오와 관찰 지표 파이프라인이 정상인지 확인 | `smoke.js` |
| Load | 일정한 정상 부하에서 p95/p99와 결과 분포 확인 | `load-baseline.js` |
| Stress | 부하를 올리며 시스템 한계와 병목 지점 확인 | 예정 |
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

## Load Capacity Baseline

Load baseline은 특정 로컬 Compose 조건에서 시스템이 어느 정도의 요청률을 안정적으로 처리하는지 확인한다.

감당 가능 기준:

- `checks == 100%`
- `http_req_failed == 0%`
- `winnerDspId == dsp-b`
- DSP 결과 분포가 `bid=2`, `no-bid=1`, `timeout=1`, `invalid=0`, `error=0`으로 유지
- p95/p99 latency를 기록

`load-baseline.js`는 아직 latency threshold로 실패하지 않는다. 이 단계의 목적은 목표 latency를 미리 가정하는 것이 아니라, 고정 조건에서 관찰된 p95/p99를 바탕으로 이후 목표 트래픽과 latency 기준을 정하는 것이다.

실행:

```bash
RPS=10 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-baseline
```

단계적으로 올려서 현재 구현의 기준을 찾는다.

```bash
RPS=10 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-baseline
RPS=30 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-baseline
RPS=50 DURATION=1m docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-baseline
```

VUs가 부족하면 k6가 요청을 충분히 만들지 못한다. 그 경우 다음 값을 함께 조정한다.

```bash
RPS=100 DURATION=2m PRE_ALLOCATED_VUS=150 MAX_VUS=300 \
  docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-baseline
```

이 결과는 production benchmark가 아니라 local reproducible baseline이다. 같은 머신에서 k6, SSP, DSP, Prometheus, Grafana가 함께 실행되므로 절대 처리량 보장으로 해석하지 않는다.
