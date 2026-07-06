package com.bbororo.rtb.ssp.adapter.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.Executors;

public final class JdkSspHttpServer {

    public static final String AUCTION_PATH = "/openrtb/auction";

    private final HttpServer server;

    public JdkSspHttpServer(int port, HttpHandler auctionHandler) {
        this(createServer(port), auctionHandler, null);
    }

    public JdkSspHttpServer(int port, HttpHandler auctionHandler, HttpHandler metricsHandler) {
        this(createServer(port), auctionHandler, metricsHandler);
    }

    JdkSspHttpServer(HttpServer server, HttpHandler auctionHandler, HttpHandler metricsHandler) {
        this.server = Objects.requireNonNull(server, "server must not be null");
        this.server.createContext(AUCTION_PATH, Objects.requireNonNull(auctionHandler, "auctionHandler must not be null"));
        if (metricsHandler != null) {
            this.server.createContext("/metrics", metricsHandler);
        }
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
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
            throw new IllegalStateException("Failed to create SSP HTTP server on port " + port, e);
        }
    }
}
