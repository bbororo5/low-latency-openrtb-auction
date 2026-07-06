# Capacity Scenario Stress - 2026-07-06

## Question

기존 load baseline은 매 요청마다 `dsp-d=timeout`을 포함했다.

그 결과 스트레스 테스트가 순수 경매 처리량보다 timeout 대기 비용을 먼저 측정했다.

이번 실험의 질문은 다음과 같다.

```text
timeout DSP를 제외하면 현재 AWS 인스턴스에서 어느 구간까지 안정적으로 부하를 만들고 처리할 수 있는가?
```

## Change

Capacity 시나리오를 timeout resilience 시나리오와 분리했다.

| Scenario | Script | DSP topology | Purpose |
|---|---|---|---|
| Capacity | `performance/k6/load-capacity.js` | `dsp-a`, `dsp-b`, `dsp-c` | 순수 처리량 한계 확인 |
| Timeout resilience | `performance/k6/load-baseline.js` | `dsp-a`, `dsp-b`, `dsp-c`, `dsp-d=timeout` | 느린 DSP 격리 확인 |

Capacity 기준 응답 분포:

```text
bid=2, no-bid=1, timeout=0, invalid=0, error=0
```

## Environment

| Item | Value |
|---|---|
| Target | AWS EC2 `m7i-flex.large` |
| Resources | 2 vCPU, 8 GiB memory |
| Target endpoint | `https://13-125-82-244.sslip.io/openrtb/auction` |
| Load generator | Grafana Cloud k6 |
| k6 load zone | `amazon:us:columbus` |
| k6 VU limit | `100 VUs` |
| Duration | `30s` per run |

The target system was redeployed with:

```text
DSP_ENDPOINTS=dsp-a=http://dsp-a:8081/openrtb/bid,dsp-b=http://dsp-b:8081/openrtb/bid,dsp-c=http://dsp-c:8081/openrtb/bid
```

## Sanity Check

Before stress runs, one manual HTTPS request returned:

```text
status=WINNER
winnerDspId=dsp-b
elapsedMs=12~33ms
bid=2, no-bid=1, timeout=0, invalid=0, error=0
```

This confirmed that the timeout DSP was removed from the capacity path.

## Runs

| RPS | Run URL | Result | Warning | Interpretation |
|---:|---|---|---|---|
| 500 | `https://curiouscicada2096.grafana.net/a/k6-app/runs/8043821` | not accepted as stable | VU shortage, request timeout, DNS error | Too high for current test path |
| 200 | `https://curiouscicada2096.grafana.net/a/k6-app/runs/8043838` | not accepted as stable | VU shortage | Load generator could not maintain target arrival rate cleanly |
| 150 | `https://curiouscicada2096.grafana.net/a/k6-app/runs/8043857` | stable candidate | none observed in CLI output | Stable under current Cloud k6 100 VU condition |
| 180 | `https://curiouscicada2096.grafana.net/a/k6-app/runs/8043861` | not accepted as stable | VU shortage | Above clean load-generation boundary |
| 165 | `https://curiouscicada2096.grafana.net/a/k6-app/runs/8043869` | stable candidate | none observed in CLI output | Highest clean run observed in this session |

## Interpretation

The current clean boundary is:

```text
165 RPS: no warning observed
180 RPS: k6 VU shortage observed
```

This does not prove that the target server fails at `180 RPS`.

It proves that, with the current Grafana Cloud k6 project limit of `100 VUs`, the load generator cannot cleanly maintain the `180 RPS` capacity scenario.

The target system's true capacity may be higher, but proving that requires one of the following:

- increasing the Grafana Cloud k6 VU limit
- running a separate load generator closer to the AWS target
- reducing client-side overhead in the test path
- collecting host-level CPU, network, and socket queue metrics during the run

## Metric Note

A Prometheus 10-minute query after the runs returned mixed values:

```text
SSP auction p95 ~= 194ms
SSP auction p99 ~= 332ms
winner rate ~= 37.9/s
no-winner rate ~= 2.27/s
```

This window includes unstable 500/200 RPS attempts, so it should not be used as the clean 165 RPS latency result.

Use the Grafana Cloud k6 run page as the source of truth for each run's latency and check details.
