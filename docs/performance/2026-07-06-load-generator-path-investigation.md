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

The investigation separated the path into four tests.

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
| HTTP failures | `1.31%` in the reproduced run |
| Failure type | `dial: i/o timeout` |
| Failed requests | `59 / 4,501` |
| k6 `http_req_connecting` p95 | `37.38ms` |
| k6 `http_req_waiting` p95 | `137.17ms` |
| Successful-request p99 latency | `180.81ms` |

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

### 3. Local Mac TCP Observation

The local Mac was sampled during an external Docker k6 run with:

```bash
netstat -anv -p tcp | grep '13.125.82.244.8080'
docker stats
uptime
```

Observed:

| Signal | Observation |
|---|---|
| Owning process | `com.docker.backend` |
| Stable connections | about `199-220 ESTABLISHED` |
| Pending connects | intermittent `SYN_SENT` |
| Docker k6 CPU | about `14-20%` |
| Mac load average | about `2.3-2.8` |

`SYN_SENT` means the client sent a TCP connection request but had not yet received the handshake response. This matches the k6 `dial: i/o timeout` symptom.

This does not prove exactly where the packet was delayed or lost, but it proves that the observed failure was on the connection-creation path before the SSP handler could process a request.

### 4. Native Mac k6 Comparison

To separate k6 itself from Docker Desktop networking, the same script was executed with native k6 installed by Homebrew.

```text
local Mac
-> native k6 process
-> public internet
-> AWS EC2 public IP:8080
-> SSP
```

Observed at `150 RPS / 30s`:

| Metric | Docker k6 | Native k6 |
|---|---:|---:|
| Requests | `4,501` | `4,500` |
| HTTP failures | `1.31%` | `0%` |
| Failed requests | `59` | `0` |
| p95 latency | `152.26ms` | `153.41ms` |
| p99 latency | `180.81ms` | `179.74ms` |
| k6 `http_req_connecting` p95 | `37.38ms` | `36.21ms` |

Native k6 still showed short-lived `SYN_SENT` states during sampling, but they did not become request failures in this run.

This narrows the reproducible failure to the local Docker-based load-generator path, not to the k6 script itself and not to the SSP auction handler.

### 5. Internal Load Generator Path

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

The local Docker k6 path failed at `150 RPS / 30s`, while native Mac k6 passed the same test.

The external failures are therefore more likely related to:

- local Mac Docker networking
- client-side connection creation
- public internet path between local machine and AWS
- NAT/router/ISP behavior
- public EC2 connection path

The current evidence does not support the claim that the SSP auction logic failed at `150 RPS`.

The strongest current conclusion is:

```text
Docker k6 on the local Mac is not a reliable source of truth for the target system's capacity.
```

## Decision

Keep the external `100 RPS / 30s` result as the conservative reproducible baseline from the local load generator.

Do not claim that the target system's true capacity is `100 RPS`.

Use the internal AWS k6 result as evidence that the next bottleneck to investigate is the load-generator path, not the auction hot path.

For local external testing, prefer native k6 over Docker k6 when the goal is to measure the deployed target system.

## Next Investigation

Recommended next steps:

```text
1. Run k6 from a separate AWS instance in the same region.
2. Compare public-IP traffic vs private-IP traffic.
3. Add node_exporter or host-level TCP metrics.
4. Check client-side connection reuse and connection timing.
```
