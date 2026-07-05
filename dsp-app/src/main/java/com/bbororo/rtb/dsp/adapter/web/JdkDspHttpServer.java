package com.bbororo.rtb.dsp.adapter.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;

public final class JdkDspHttpServer {

    public static final String BID_PATH = "/openrtb/bid";

    private final HttpServer server;

    public JdkDspHttpServer(int port, HttpHandler bidHandler) {
        this(createServer(port), bidHandler, null);
    }

    public JdkDspHttpServer(int port, HttpHandler bidHandler, HttpHandler metricsHandler) {
        this(createServer(port), bidHandler, metricsHandler);
    }

    JdkDspHttpServer(HttpServer server, HttpHandler bidHandler, HttpHandler metricsHandler) {
        this.server = Objects.requireNonNull(server, "server must not be null");
        this.server.createContext(BID_PATH, Objects.requireNonNull(bidHandler, "bidHandler must not be null"));
        if (metricsHandler != null) {
            this.server.createContext("/metrics", metricsHandler);
        }
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    private static HttpServer createServer(int port) {
        try {
            return HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create DSP HTTP server on port " + port, e);
        }
    }
}
