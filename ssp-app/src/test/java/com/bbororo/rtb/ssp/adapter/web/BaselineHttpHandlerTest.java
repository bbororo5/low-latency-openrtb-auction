package com.bbororo.rtb.ssp.adapter.web;

import com.bbororo.rtb.shared.openrtb.codec.JacksonOpenRtbJsonCodec;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineHttpHandlerTest {

    @Test
    void ok_handler_returns_plain_ok() throws Exception {
        try (TestServer server = TestServer.start("/ok", new OkHttpHandler())) {
            HttpResponse<String> response = get(server.uri("/ok"));

            assertEquals(200, response.statusCode());
            assertEquals("OK", response.body());
            assertEquals("text/plain; charset=utf-8", response.headers().firstValue("Content-Type").orElse(""));
        }
    }

    @Test
    void json_baseline_decodes_openrtb_request_and_returns_fixed_bid_response() throws Exception {
        try (TestServer server = TestServer.start(
                "/baseline/openrtb-json",
                new OpenRtbJsonBaselineHttpHandler(new JacksonOpenRtbJsonCodec())
        )) {
            HttpResponse<String> response = postJson(server.uri("/baseline/openrtb-json"), """
                    {
                      "id": "req-baseline-001",
                      "imp": [
                        {
                          "id": "imp-001",
                          "banner": {"w": 300, "h": 250},
                          "bidfloor": 0.5,
                          "bidfloorcur": "USD"
                        }
                      ],
                      "tmax": 120,
                      "at": 1
                    }
                    """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"req-baseline-001\""));
            assertTrue(response.body().contains("\"seat\":\"baseline-seat\""));
            assertTrue(response.body().contains("\"impid\":\"imp-001\""));
        }
    }

    @Test
    void json_baseline_rejects_invalid_json() throws Exception {
        try (TestServer server = TestServer.start(
                "/baseline/openrtb-json",
                new OpenRtbJsonBaselineHttpHandler(new JacksonOpenRtbJsonCodec())
        )) {
            HttpResponse<String> response = postJson(server.uri("/baseline/openrtb-json"), "{");

            assertEquals(400, response.statusCode());
        }
    }

    private static HttpResponse<String> get(URI uri) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> postJson(URI uri, String body) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(2))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;

        private TestServer(HttpServer server) {
            this.server = server;
        }

        static TestServer start(String path, HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext(path, handler);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return new TestServer(server);
        }

        URI uri(String path) {
            return URI.create("http://localhost:" + server.getAddress().getPort() + path);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
