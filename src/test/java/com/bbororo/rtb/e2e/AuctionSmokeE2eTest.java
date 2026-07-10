package com.bbororo.rtb.e2e;

import com.bbororo.rtb.dsp.DspApplication;
import com.bbororo.rtb.dsp.DspApplication.DspMode;
import com.bbororo.rtb.dsp.DspApplication.DspRuntimeConfig;
import com.bbororo.rtb.dsp.adapter.web.JdkDspHttpServer;
import com.bbororo.rtb.ssp.SspApplication;
import com.bbororo.rtb.ssp.adapter.web.JdkSspHttpServer;
import com.bbororo.rtb.ssp.dspgateway.DspEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionSmokeE2eTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROVIDER_REQUEST = """
            {
              "providerId": "publisher-demo",
              "placementId": "home-top-banner",
              "mediaType": "banner",
              "width": 300,
              "height": 250,
              "tmax": 300
            }
            """;

    @Test
    void returns_highest_valid_bid_over_the_provider_http_path() throws Exception {
        try (RunningTopology topology = topology(DspMode.NORMAL_MEDIUM, DspMode.NORMAL_HIGH, DspMode.NO_BID)) {
            JsonNode result = topology.auction();

            assertEquals("WINNER", result.path("status").asText());
            assertEquals("dsp-b", result.path("winnerDspId").asText());
            assertEquals(2, result.path("dspResultCounts").path("validBidCount").asInt());
            assertEquals(1, result.path("dspResultCounts").path("noBidCount").asInt());
            assertEquals(3, terminalCount(result));
            assertTrue(topology.metrics().contains("rtb_ssp_dsp_terminal_result_total"));
        }
    }

    @Test
    void returns_no_winner_when_all_dsps_return_no_bid() throws Exception {
        try (RunningTopology topology = topology(DspMode.NO_BID, DspMode.NO_BID)) {
            JsonNode result = topology.auction();

            assertEquals("NO_WINNER", result.path("status").asText());
            assertEquals(0, result.path("dspResultCounts").path("validBidCount").asInt());
            assertEquals(2, result.path("dspResultCounts").path("noBidCount").asInt());
            assertEquals(2, terminalCount(result));
        }
    }

    @Test
    void preserves_a_valid_winner_when_another_dsp_times_out() throws Exception {
        try (RunningTopology topology = topology(DspMode.NORMAL_HIGH, DspMode.TIMEOUT)) {
            JsonNode result = topology.auction();

            assertEquals("WINNER", result.path("status").asText());
            assertEquals("dsp-a", result.path("winnerDspId").asText());
            assertEquals(1, result.path("dspResultCounts").path("validBidCount").asInt());
            assertEquals(1, result.path("dspResultCounts").path("timeoutCount").asInt());
            assertEquals(2, terminalCount(result));
        }
    }

    private static RunningTopology topology(DspMode... modes) throws IOException {
        List<JdkDspHttpServer> dspServers = new ArrayList<>();
        List<DspEndpoint> endpoints = new ArrayList<>();
        for (int index = 0; index < modes.length; index++) {
            int port = freePort();
            String dspId = "dsp-" + (char) ('a' + index);
            JdkDspHttpServer dspServer = DspApplication.createServer(new DspRuntimeConfig(dspId, port, modes[index]));
            dspServer.start();
            dspServers.add(dspServer);
            endpoints.add(new DspEndpoint(dspId, URI.create("http://localhost:" + port + "/openrtb/bid")));
        }

        int sspPort = freePort();
        JdkSspHttpServer sspServer = SspApplication.createServer(sspPort, endpoints);
        sspServer.start();
        return new RunningTopology(sspPort, sspServer, dspServers);
    }

    private static int terminalCount(JsonNode result) {
        JsonNode counts = result.path("dspResultCounts");
        return counts.path("validBidCount").asInt()
                + counts.path("noBidCount").asInt()
                + counts.path("timeoutCount").asInt()
                + counts.path("invalidBidCount").asInt()
                + counts.path("errorCount").asInt();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record RunningTopology(
            int sspPort,
            JdkSspHttpServer sspServer,
            List<JdkDspHttpServer> dspServers
    ) implements AutoCloseable {

        private JsonNode auction() throws Exception {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://localhost:" + sspPort + "/publisher/auction"))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(PROVIDER_REQUEST))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            return OBJECT_MAPPER.readTree(response.body());
        }

        private String metrics() throws Exception {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://localhost:" + sspPort + "/metrics"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            return response.body();
        }

        @Override
        public void close() {
            sspServer.stop(0);
            dspServers.forEach(server -> server.stop(0));
        }
    }
}
