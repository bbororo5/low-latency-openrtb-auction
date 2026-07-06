# AWS HttpServer Executor Investigation

Date: 2026-07-06

## Context

Local Compose load testing showed that k6 end-to-end latency increased sharply at `10 RPS`, while SSP internal auction latency remained near the auction deadline.

The target system was deployed to AWS EC2 to separate the load generator from the target system.

## Environment

| Item | Value |
|---|---|
| Target | AWS EC2 |
| Instance type | `m7i-flex.large` |
| Region | `ap-northeast-2` |
| Topology | SSP + 4 DSP containers |
| Load generator | local k6 container |
| Scenario | `performance/k6/load-baseline.js` |

## Initial Observation

At `10 RPS / 30s`, k6 showed high end-to-end latency even though the auction result remained correct.

| Metric | Before executor change |
|---|---:|
| k6 p95 | `3.44s` |
| k6 p99 | `4.03s` |
| failed requests | `0%` |
| dropped iterations | `4` |

Prometheus showed that SSP internal auction latency was much lower than k6 end-to-end latency.

| Metric | Before executor change |
|---|---:|
| SSP auction winner p95 | about `132ms` |
| normal DSP call p95 | about `13ms` |

This indicated that the main delay was not inside the auction hot path.

## JVM Thread Observation

The SSP thread dump showed the following stack under `HTTP-Dispatcher`:

```text
HTTP-Dispatcher
  -> ServerImpl$DefaultExecutor.execute
  -> OpenRtbAuctionHttpHandler.handle
  -> DefaultAuctionFlow.run
  -> HttpDspGateway.waitUntilDeadline
  -> CompletableFuture.get
```

This means the JDK HttpServer default executor did not hand request handling to a separate worker pool. The dispatcher thread executed the handler task and waited for the DSP deadline.

Because each auction includes one intentionally timing-out DSP, one request can occupy the dispatcher path for about `120ms`.

## Change

The SSP and DSP JDK HttpServer instances now use a virtual-thread-per-task executor.

```java
server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
```

This separates request dispatch from request handling:

```text
HTTP-Dispatcher -> dispatch only
Virtual thread  -> HttpHandler execution
```

## Result

After redeploying the change to AWS, the same `10 RPS / 30s` load test improved significantly.

| Metric | Before | After |
|---|---:|---:|
| k6 avg | `1.82s` | `152.62ms` |
| k6 p95 | `3.44s` | `171.74ms` |
| k6 p99 | `4.03s` | `203.55ms` |
| failed requests | `0%` | `0%` |
| dropped iterations | `4` | `0` |

Thread dump after the change showed `HTTP-Dispatcher` waiting in `EPoll/select`, not inside the auction handler.

```text
HTTP-Dispatcher
  -> EPoll.wait
  -> SelectorImpl.select
  -> ServerImpl$Dispatcher.run
```

## Conclusion

The sharp latency increase was caused by the JDK HttpServer default executor execution model, not by the auction decision logic.

The fix was to explicitly configure the HTTP server executor so that blocking DSP deadline waits do not occupy the dispatcher thread.

This is an execution-model problem:

```text
arrival rate > dispatcher-bound service rate
=> queueing delay
```

After the executor change, the system handled `10 RPS` without request queue buildup in this AWS test environment.
