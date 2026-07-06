# Load Generator Path Investigation

Date: 2026-07-06

## Question

External k6 tests from the local Mac showed `dial: i/o timeout` at `150 RPS` and `200 RPS`.

This error happens while the client is creating a TCP connection. It does not mean that the SSP auction handler returned a slow response.

The investigation question was:

```text
Is the target system failing, or is the load generator / public connection path failing?
```

## Method

The investigation separated the path into two tests.

### 1. External Load Generator Path

```text
local Mac
-> Docker k6 container
-> public internet
-> AWS EC2 public IP:8080
-> SSP
```

Observed at `150 RPS / 30s`:

| Metric | Value |
|---|---:|
| HTTP failures | `1.88%` |
| Failure type | `dial: i/o timeout` |
| k6 `http_req_connecting` p95 | `36.86ms` |
| k6 `http_req_waiting` p95 | `137.49ms` |

The successful requests were still handled normally. The failure happened while opening connections.

### 2. Server-Side Socket Observation

During the external test, the EC2 server was sampled with:

```bash
ss -ltn sport = :8080
ss -tan sport = :8080
cat /proc/net/netstat
docker stats
```

Observed:

| Signal | Observation |
|---|---|
| Listen queue `Recv-Q` | `0` |
| Listen backlog | `4096` |
| `ListenOverflows` | `0` |
| `ListenDrops` | `0` |
| SSP CPU | about `20-30%` during active load |

There was no clear evidence that the EC2 kernel listen queue was dropping connections.

### 3. Internal Load Generator Path

To remove the public internet path and the local Mac Docker path, k6 was also executed inside the AWS Compose network.

```text
AWS EC2
-> k6 container
-> Docker network
-> SSP container
```

Observed at `150 RPS / 30s`:

| Metric | Value |
|---|---:|
| Requests | `4,500` |
| HTTP failures | `0%` |
| Checks | `100%` |
| p95 latency | `120.12ms` |
| p99 latency | `121.76ms` |

Observed at `200 RPS / 30s`:

| Metric | Value |
|---|---:|
| Requests | `6,001` |
| HTTP failures | `0%` |
| Checks | `100%` |
| p95 latency | `120.05ms` |
| p99 latency | `121.07ms` |

## Interpretation

The target system did not fail at `150 RPS` or `200 RPS` when the load generator ran inside the AWS network.

The external failures are therefore more likely related to:

- local Mac Docker networking
- client-side connection creation
- public internet path between local machine and AWS
- NAT/router/ISP behavior
- public EC2 connection path

The current evidence does not support the claim that the SSP auction logic failed at `150 RPS`.

## Decision

Keep the external `100 RPS / 30s` result as the conservative reproducible baseline from the local load generator.

Do not claim that the target system's true capacity is `100 RPS`.

Use the internal AWS k6 result as evidence that the next bottleneck to investigate is the load-generator path, not the auction hot path.

## Next Investigation

Recommended next steps:

```text
1. Run k6 from a separate AWS instance in the same region.
2. Compare public-IP traffic vs private-IP traffic.
3. Add node_exporter or host-level TCP metrics.
4. Check client-side connection reuse and connection timing.
```
