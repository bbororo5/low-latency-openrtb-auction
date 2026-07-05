package com.bbororo.rtb.dsp.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

public final class DelayedHttpHandler implements HttpHandler {

    private final HttpHandler delegate;
    private final Duration delay;

    public DelayedHttpHandler(HttpHandler delegate, Duration delay) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.delay = Objects.requireNonNull(delay, "delay must not be null");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        delegate.handle(exchange);
    }
}
