package com.bbororo.rtb.ssp.adapter.web;

import com.bbororo.rtb.shared.openrtb.Bid;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.BidResponse;
import com.bbororo.rtb.shared.openrtb.SeatBid;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodec;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodecException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class OpenRtbJsonBaselineHttpHandler implements HttpHandler {

    private static final String POST = "POST";
    private static final String APPLICATION_JSON = "application/json";

    private final OpenRtbJsonCodec codec;

    public OpenRtbJsonBaselineHttpHandler(OpenRtbJsonCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!POST.equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 405);
                return;
            }

            try {
                BidRequest request = decodeRequest(exchange);
                sendJson(exchange, fixedResponse(request));
            } catch (OpenRtbJsonCodecException e) {
                sendEmpty(exchange, 400);
            }
        }
    }

    private BidRequest decodeRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return codec.decodeRequest(body);
    }

    private BidResponse fixedResponse(BidRequest request) {
        String impId = request.imp().isEmpty() ? "imp-001" : request.imp().getFirst().id();
        return new BidResponse(
                request.id(),
                List.of(new SeatBid(
                        "baseline-seat",
                        List.of(new Bid(
                                "baseline-bid-001",
                                impId,
                                new BigDecimal("1.00"),
                                "baseline-campaign",
                                "baseline-creative",
                                List.of("baseline.example"),
                                1,
                                "<div>baseline</div>"
                        ))
                )),
                "USD"
        );
    }

    private void sendJson(HttpExchange exchange, BidResponse bidResponse) throws IOException {
        byte[] bytes = codec.encodeResponse(bidResponse).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", APPLICATION_JSON);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sendEmpty(HttpExchange exchange, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, -1);
    }
}
