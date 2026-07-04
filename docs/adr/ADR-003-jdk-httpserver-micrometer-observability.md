# ADR-003: Use JDK HttpServer with Micrometer for Baseline Observability

## Status

Accepted for baseline. Revisit after measurement.

## Context

SSP and DSP applications must communicate over OpenRTB-compatible HTTP/JSON.

The system must measure p95/p99 latency, deadline compliance, and the distribution of timeout, no-bid, invalid bid, and error results. At the same time, the baseline should avoid unnecessary HTTP server framework overhead in the low-latency hot path.

Spring Boot Actuator makes Prometheus integration and default metrics convenient, but it brings the Spring Boot HTTP stack into the baseline. Using only JDK HttpServer and a fully custom metrics registry keeps the runtime small, but requires custom Prometheus formatting and metric management.

## Options

| Option | Trade-off |
|---|---|
| Spring Boot Actuator + Micrometer | Observability is convenient, but the HTTP server stack becomes heavier. |
| JDK HttpServer + custom metrics | Smallest runtime, but metric implementation and Prometheus format management become project work. |
| JDK HttpServer + Micrometer | Keeps the HTTP server small while delegating metric recording and Prometheus exposition to a proven library. |
| Netty + Micrometer | More network control, but too much implementation complexity for the baseline. |

## Decision

The baseline uses `JDK HttpServer + Micrometer Prometheus Registry`.

- SSP/DSP HTTP endpoints are served by JDK `HttpServer`.
- RTB domain metrics are recorded with Micrometer `Timer` and `Counter`.
- `/metrics` exposes Prometheus scrape format.
- Prometheus and Grafana are connected in the monitoring phase.
- Spring Boot Actuator and Netty are excluded from the baseline.

## Consequences

Positive:

- Prometheus/Grafana integration remains possible without Spring Boot.
- The HTTP server stack stays small for the baseline.
- Prometheus text format does not need to be implemented manually.
- RTB domain metrics can be designed explicitly.

Negative:

- HTTP/JVM metrics are not automatically as rich as with Actuator.
- `/metrics` and HTTP request instrumentation must be wired manually.
- Production-grade health checks, tracing, and log aggregation are not included.

## Revisit Conditions

Revisit this decision if one of the following is observed:

- JDK HttpServer becomes a p95/p99 latency bottleneck.
- Manual HTTP instrumentation becomes hard to maintain.
- JVM, GC, or thread metrics become important for the baseline.
- Operational convenience becomes more important than keeping the HTTP stack minimal.
- Netty or Spring Boot becomes a better measured trade-off.
