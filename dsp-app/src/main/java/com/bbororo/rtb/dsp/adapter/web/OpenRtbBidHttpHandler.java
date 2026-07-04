package com.bbororo.rtb.dsp.adapter.web;

import com.bbororo.rtb.dsp.bidhandler.BidAccepted;
import com.bbororo.rtb.dsp.bidhandler.BidHandler;
import com.bbororo.rtb.dsp.bidhandler.BidHandlingResult;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodec;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodecException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class OpenRtbBidHttpHandler implements HttpHandler {

    private static final String POST = "POST";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final OpenRtbJsonCodec codec;
    private final BidHandler bidHandler;

    public OpenRtbBidHttpHandler(OpenRtbJsonCodec codec, BidHandler bidHandler) {
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.bidHandler = Objects.requireNonNull(bidHandler, "bidHandler must not be null");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!POST.equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 405);
                return;
            }

            BidRequest bidRequest = decodeRequest(exchange);
            BidHandlingResult result = bidHandler.handle(bidRequest);

            if (result instanceof BidAccepted bidAccepted) {
                sendJson(exchange, 200, codec.encodeResponse(bidAccepted.bidResponse()));
                return;
            }

            sendEmpty(exchange, 204);
        } catch (OpenRtbJsonCodecException e) {
            sendEmpty(exchange, 400);
        } catch (RuntimeException e) {
            sendEmpty(exchange, 500);
        }
    }

    private BidRequest decodeRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return codec.decodeRequest(body);
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(CONTENT_TYPE, APPLICATION_JSON);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sendEmpty(HttpExchange exchange, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, -1);
    }
}
