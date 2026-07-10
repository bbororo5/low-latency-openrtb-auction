# Grafana Cloud k6 Baseline

Date: 2026-07-06

## Question

Local Docker k6 was not a reliable source of truth for deployed target-system capacity because it produced client-side `dial: i/o timeout` failures.

The next question was:

```text
Can the load generator be moved out of the local Mac path and into Grafana Cloud k6?
```

## Deployed Topology

```text
Grafana Cloud k6
 -> https://13-125-82-244.sslip.io/openrtb/auction
 -> AWS EC2 Caddy
 -> SSP
 -> DSP-A/B/C/D
```

Monitoring topology:

```text
Grafana Cloud Metrics Endpoint
 -> https://13-125-82-244.sslip.io/metrics/*
 -> AWS EC2 Caddy
 -> SSP/DSP metrics endpoints
```

EC2 no longer runs Grafana or Prometheus for the AWS performance setup.

## Cloud k6 Authentication

The local k6 CLI is authenticated to Grafana Cloud k6.

```text
Stack URL: https://curiouscicada2096.grafana.net
Default project ID: 8018933
```

The token is stored in the local k6 config and is not committed to the repository.

## Smoke Run

Command:

```bash
k6 cloud run \
  -e BASE_URL=https://13-125-82-244.sslip.io \
  performance/k6/smoke.js
```

Observed:

| Field | Value |
|---|---|
| Run URL | `https://curiouscicada2096.grafana.net/a/k6-app/runs/8041428` |
| Final status | `Finished` |
| Test name | `smoke.js` |

## Baseline Run

Command:

```bash
k6 cloud run \
  -e BASE_URL=https://13-125-82-244.sslip.io \
  -e RPS=10 \
  -e DURATION=30s \
  -e PRE_ALLOCATED_VUS=30 \
  -e MAX_VUS=100 \
  performance/k6/load-baseline.js
```

Observed:

| Field | Value |
|---|---|
| Run URL | `https://curiouscicada2096.grafana.net/a/k6-app/runs/8041454` |
| Final status | `Finished` |
| Test name | `load-baseline.js` |
| Configured rate | `10 RPS` |
| Configured duration | `30s` |

The k6 CLI did not print the full local summary for the cloud run. Detailed latency, checks, and threshold results should be read from the Grafana Cloud k6 run page.

## Metrics Endpoint Verification

Grafana Cloud Metrics Endpoint is scraping all five target jobs.

Query:

```promql
up{scrape_job=~"rtb-.*"}
```

Observed:

| Scrape job | Value |
|---|---:|
| `rtb-ssp` | `1` |
| `rtb-dsp-a` | `1` |
| `rtb-dsp-b` | `1` |
| `rtb-dsp-c` | `1` |
| `rtb-dsp-d` | `1` |

Application metric names are visible in Grafana Cloud, including:

```text
rtb_ssp_auction_result_total
rtb_ssp_dsp_call_result_total
rtb_dsp_bid_result_total
rtb_ssp_auction_duration_seconds_*
rtb_ssp_dsp_call_duration_seconds_*
rtb_dsp_bid_handle_duration_seconds_*
```

## Current Interpretation

Grafana Cloud k6 is now the preferred load generator for AWS performance tests.

This removes the local Mac and Docker Desktop networking path from the load test path.

The current AWS performance setup is:

```text
Load generator: Grafana Cloud k6
Target system: AWS EC2 SSP/DSP containers behind Caddy HTTPS
Monitoring: Grafana Cloud Metrics Endpoint and managed metrics storage
```

## Follow-up

Next performance work should use Grafana Cloud k6 run pages as the source of truth for:

- request rate
- failed request rate
- checks
- p95/p99 latency
- load zone

Each future baseline should record the Grafana Cloud k6 run URL and the target AWS region.
