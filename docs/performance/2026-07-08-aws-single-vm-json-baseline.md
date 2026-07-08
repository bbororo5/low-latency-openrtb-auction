# AWS Single VM JSON Baseline - 2026-07-08

## Question

전통적인 JSON over HTTP 방식에서, 같은 `m7i-flex.large` EC2 안에 target system과 k6 load generator를 함께 두면 baseline 계층별 RPS와 latency가 어떻게 갈라지는가?

이번 실험은 production capacity 주장이 아니다. target과 load generator가 같은 2 vCPU VM을 공유하므로, 결과는 "single VM self-load baseline"으로 해석한다.

## Environment

| Item | Value |
|---|---|
| EC2 instance | `i-02f95d00ce0680b76` |
| Instance type | `m7i-flex.large` |
| Region / AZ | `ap-northeast-2 / ap-northeast-2a` |
| vCPU / memory | `2 vCPU / 7.6 GiB` |
| OS | Amazon Linux 2023 |
| Runtime | Docker `25.0.14`, Docker Compose `v2.39.4` |
| Target topology | SSP + DSP-A/B/C/D containers |
| Capacity topology | SSP uses only DSP-A/B/C |
| Load generator | `grafana/k6:1.2.1` container on the same Docker network |
| Test duration | `30s` per RPS step |

Capacity topology:

```text
k6 container
 -> SSP /openrtb/auction
 -> DSP-A normal bid
 -> DSP-B high bid
 -> DSP-C no-bid
```

Expected auction result:

```text
status=WINNER
winnerDspId=dsp-b
dspResultCounts: bid=2, no_bid=1, timeout=0, invalid=0, error=0
```

## Zero-base Results

`GET /ok` excludes JSON codec, OpenRTB object construction, auction logic, and DSP fan-out.

| RPS | Requests | Checks | HTTP failed | Dropped | p95 | p99 | Result |
|---:|---:|---:|---:|---:|---:|---:|---|
| 100 | 3,001 | 100.00% | 0.00% | 0 | 0.24ms | 0.30ms | stable |
| 1000 | 30,001 | 100.00% | 0.00% | 0 | 0.25ms | 2.10ms | stable |
| 2000 | 60,000 | 100.00% | 0.00% | 0 | 1.69ms | 6.72ms | stable |

`POST /baseline/openrtb-json` decodes an OpenRTB JSON request and returns a fixed OpenRTB JSON bid response. It excludes auction flow, DSP fan-out, and winner selection.

| RPS | Requests | Checks | HTTP failed | Dropped | p95 | p99 | Result |
|---:|---:|---:|---:|---:|---:|---:|---|
| 100 | 3,001 | 100.00% | 0.00% | 0 | 0.48ms | 0.68ms | stable |
| 1000 | 30,001 | 100.00% | 0.00% | 0 | 0.98ms | 5.96ms | stable |
| 2000 | 59,979 | 100.00% | 0.00% | 0 | 4.00ms | 12.83ms | stable |

## RTB JSON Capacity Threshold

Each threshold step recreated the SSP container before the run. This avoids carrying over the non-recovering failure state observed after overload.

| RPS | Requests | Checks | HTTP failed | Dropped | p95 | p99 | Result |
|---:|---:|---:|---:|---:|---:|---:|---|
| 10 | 301 | 100.00% | 0.00% | 0 | 10.97ms | 13.72ms | stable |
| 30 | 901 | 100.00% | 0.00% | 0 | 9.46ms | 49.82ms | stable |
| 50 | 1,501 | 100.00% | 0.00% | 0 | 9.99ms | 56.09ms | stable |
| 75 | 2,251 | 100.00% | 0.00% | 0 | 8.70ms | 57.80ms | stable |
| 100 | 3,001 | 100.00% | 0.00% | 0 | 5.59ms | 56.64ms | stable baseline |
| 105 | 3,151 | 98.86% | 0.00% | 0 | 60.07ms | 196.22ms | failed |
| 110 | 3,301 | 98.92% | 0.00% | 0 | 61.58ms | 213.41ms | failed |
| 125 | 3,751 | 98.52% | 0.00% | 0 | 10.54ms | 253.56ms | failed |
| 140 | 4,201 | 98.09% | 0.00% | 0 | 68.70ms | 337.85ms | failed |
| 150 | 4,500 | 97.28% | 0.00% | 0 | 138.09ms | 330.03ms | failed |
| 200 | 6,001 | 94.29% | 0.00% | 0 | 229.15ms | 437.76ms | failed |
| 300 | 8,938 | 88.80% | 0.00% | 63 | 747.76ms | 1276.56ms | failed |
| 400 | 11,852 | 37.52% | 0.00% | 149 | 1125.71ms | 1551.01ms | failed |
| 500 | 14,811 | 37.50% | 0.00% | 190 | 785.01ms | 2117.55ms | failed |

Current single-VM JSON capacity threshold:

```text
Stable: 100 RPS / 30s
First observed failure: 105 RPS / 30s
```

## Interpretation

The zero-base layers show that this VM, Docker network, JVM, JDK HttpServer, HTTP/1.1, and JSON codec path can handle `2000 RPS / 30s` with `checks=100%` and `http_req_failed=0%`.

The actual RTB JSON path fails at `105 RPS / 30s` even though HTTP failures stay at `0%`. This means the first baseline bottleneck is not plain HTTP handling or JSON serialization. The failing layer is the SSP-DSP auction path: DSP fan-out, outbound HTTP execution, deadline handling, or how the current executor behaves under concurrent fan-out.

The important measurement rule is:

```text
HTTP success is not enough.
RTB capacity must require domain checks: winner=dsp-b and bid/no-bid/timeout/error distribution is correct.
```

After overload, the SSP was observed returning `NO_WINNER` with `errorCount=3` even for a single request until the SSP container was recreated. Treat overload recovery as a separate failure mode to investigate.

## Next Work

1. Add metrics or logs that expose per-request DSP call classification at the SSP boundary.
2. Inspect SSP outbound DSP HTTP executor saturation and rejection behavior around `105 RPS`.
3. Re-run `100`, `105`, `110` RPS after instrumenting executor state.
4. Only after the JSON baseline is explained, introduce an OpenRTB Protobuf variant and compare it against the JSON codec and RTB JSON baseline.
