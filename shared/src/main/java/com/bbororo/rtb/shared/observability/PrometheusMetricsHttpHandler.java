package com.bbororo.rtb.shared.observability;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class PrometheusMetricsHttpHandler implements HttpHandler {

    public static final String METRICS_PATH = "/metrics";

    private static final String GET = "GET";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String PROMETHEUS_TEXT_FORMAT = "text/plain; version=0.0.4; charset=utf-8";

    private final PrometheusMeterRegistry registry;

    public PrometheusMetricsHttpHandler(PrometheusMeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!GET.equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(CONTENT_TYPE, PROMETHEUS_TEXT_FORMAT);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
