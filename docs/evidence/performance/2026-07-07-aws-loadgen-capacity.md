# AWS Load Generator Capacity - 2026-07-07

## Question

Grafana Cloud k6는 target과 다른 리전에 있었고, 현재 프로젝트 한도에서는 `100 VUs` 제한도 있었다.

따라서 높은 RPS에서 관찰된 실패가 target system의 한계인지, load generator 경로의 한계인지 분리하기 어려웠다.

이번 실험의 질문은 다음과 같다.

```text
부하 발생기를 target과 같은 AWS 리전에 두고 private IP로 호출하면,
현재 target EC2 1대는 timeout 없는 capacity scenario를 어디까지 안정적으로 처리하는가?
```

## Topology

```text
AWS loadgen EC2
 -> target EC2 private IP:8080
 -> SSP
 -> DSP-A/B/C
 -> SSP
 -> AuctionResult
```

| Item | Value |
|---|---|
| Target EC2 | `m7i-flex.large`, 2 vCPU, 8 GiB |
| Loadgen EC2 | `m7i-flex.large`, 2 vCPU, 8 GiB |
| Region | `ap-northeast-2` |
| Target path | private IP HTTP |
| Scenario | `performance/k6/load-capacity.js` |
| DSP topology | `dsp-a=bid`, `dsp-b=high bid`, `dsp-c=no-bid` |
| Excluded from capacity path | `dsp-d=timeout` |

Expected response:

```text
status=WINNER
winnerDspId=dsp-b
bid=2, no-bid=1, timeout=0, invalid=0, error=0
```

## Results

| RPS | Duration | Requests | Checks | HTTP failed | p95 | p99 | Dropped | Result |
|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 100 | 1m | 6,001 | 100% | 0% | 42.65ms | 43.56ms | 0 | stable |
| 300 | 1m | 18,001 | 100% | 0% | 42.96ms | 43.39ms | 0 | stable |
| 500 | 1m | 30,002 | 100% | 0% | 43.26ms | 44.01ms | 0 | stable |
| 800 | 1m | 48,000 | 100% | 0% | 43.76ms | 46.71ms | 0 | stable |
| 900 | 1m | 54,001 | 100% | 0% | 3.15ms | 7.19ms | 0 | stable |
| 1000 | 1m | 60,001 | 99.94% | 0% | 46.20ms | 60.23ms | 0 | unstable first run |
| 1000 retry | 1m | 60,000 | 100% | 0% | 4.77ms | 11.66ms | 0 | stable |
| 1100 | 1m | 66,000 | 37.50% | 0% | 0.71ms | 2.48ms | 0 | failed fast |
| 1200 | 1m | 52,158 | 37.74% | 0% | 1668.58ms | 16457.40ms | 19,843 | failed |
| 1500 | 1m | 71,952 | 38.18% | 0% | 1636.02ms | 1779.79ms | 18,049 | failed |

## Interpretation

현재 확인한 안정 구간은 다음과 같다.

```text
1000 RPS / 1m: retry run 기준 checks 100%, HTTP failure 0%, dropped iterations 0
```

`1100 RPS` 이상은 안정 구간으로 보지 않는다.

특히 `1100 RPS`에서는 HTTP 응답 자체는 빠르게 돌아왔지만, 대부분의 응답이 정상 낙찰이 아니었다.
즉, HTTP status와 latency만 보면 좋아 보이지만 실제 경매 결과는 깨진 상태였다.

이 실험에서 중요한 점은 다음이다.

```text
성능 테스트는 HTTP 성공 여부만 보면 안 된다.
도메인 결과가 맞는지 함께 검증해야 한다.
```

## Failure Evidence

`1100 RPS` 이후 SSP 로그에서 다음 JVM 경고가 반복되었다.

```text
Failed to start thread "Unknown thread" - pthread_create failed (EAGAIN)
Failed to start the native thread for java.lang.Thread "Thread-..."
```

이 메시지는 OS/JVM이 더 이상 native thread를 만들지 못했다는 뜻이다.

이것은 단순히 "서버가 느리다"가 아니라, 병목 자원이 더 구체적으로 드러난 상태다.

```text
병목 후보: SSP의 outbound DSP fan-out 과정에서 native thread가 과도하게 생성됨
증상: DSP 호출이 error 처리되고, SSP가 빠르게 NO_WINNER 계열 응답을 반환함
```

`1200 RPS`, `1500 RPS`에서는 k6도 목표 arrival rate를 유지하지 못했고 dropped iterations가 발생했다.
따라서 이 구간의 수치는 target system 단독 한계가 아니라 target failure와 load generator pressure가 섞인 결과로 본다.

## Current Boundary

현재 보수적인 결론은 다음이다.

| Boundary | Meaning |
|---|---|
| `1000 RPS / 1m` | 현재 AWS private path에서 확인한 안정 기준선 |
| `1100 RPS+` | native thread exhaustion이 관찰된 실패 구간 |

정확한 한계점을 더 좁히려면 바로 RPS를 더 쪼개기보다, 먼저 thread 생성 원인을 계측해야 한다.

## Next Work

다음 최적화 작업은 RPS 숫자를 더 올리는 것이 아니라 thread pressure를 관찰하고 줄이는 것이다.

Recommended next steps:

```text
1. SSP의 JVM thread count와 process thread count를 metric으로 노출한다.
2. SSP outbound DSP HTTP client의 executor 사용 방식을 확인한다.
3. 필요하면 bounded executor 또는 별도 HTTP client 전략으로 fan-out concurrency를 제한한다.
4. 같은 AWS loadgen topology에서 1000, 1100, 1200 RPS를 다시 측정한다.
```
