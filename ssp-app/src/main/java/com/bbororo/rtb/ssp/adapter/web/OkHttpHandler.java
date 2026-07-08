package com.bbororo.rtb.ssp.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class OkHttpHandler implements HttpHandler {

    private static final String GET = "GET";
    private static final byte[] OK = "OK".getBytes(StandardCharsets.UTF_8);

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!GET.equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, OK.length);
            exchange.getResponseBody().write(OK);
        }
    }
}
