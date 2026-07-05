package com.bbororo.rtb.dsp.adapter.web;

import com.bbororo.rtb.dsp.bidhandler.BidAccepted;
import com.bbororo.rtb.dsp.bidhandler.BidHandler;
import com.bbororo.rtb.dsp.bidhandler.BidHandlingResult;
import com.bbororo.rtb.dsp.bidhandler.NoBid;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.Imp;
import com.bbororo.rtb.shared.observability.RtbMetricTags;
import com.bbororo.rtb.shared.observability.RtbMetrics;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodec;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodecException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class OpenRtbBidHttpHandler implements HttpHandler {

    private static final String POST = "POST";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final OpenRtbJsonCodec codec;
    private final BidHandler bidHandler;
    private final RtbMetrics metrics;

    public OpenRtbBidHttpHandler(OpenRtbJsonCodec codec, BidHandler bidHandler, RtbMetrics metrics) {
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.bidHandler = Objects.requireNonNull(bidHandler, "bidHandler must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Instant startedAt = Instant.now();
        String mediaType = RtbMetricTags.UNKNOWN;
        String resultTag = "error";
        try (exchange) {
            if (!POST.equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 405);
                return;
            }

            BidRequest bidRequest = decodeRequest(exchange);
            mediaType = mediaTypeOf(bidRequest);
            BidHandlingResult result = bidHandler.handle(bidRequest);

            if (result instanceof BidAccepted bidAccepted) {
                resultTag = "bid";
                sendJson(exchange, 200, codec.encodeResponse(bidAccepted.bidResponse()));
                return;
            }

            resultTag = "no_bid";
            if (result instanceof NoBid noBid) {
                metrics.recordDspNoBidReason(mediaType, noBid.reason().name());
            }
            sendEmpty(exchange, 204);
        } catch (OpenRtbJsonCodecException e) {
            resultTag = "invalid_request";
            metrics.recordDspNoBidReason(mediaType, "invalid_request");
            sendEmpty(exchange, 400);
        } catch (RuntimeException e) {
            sendEmpty(exchange, 500);
        } finally {
            metrics.recordDspBidHandling(Duration.between(startedAt, Instant.now()), mediaType, resultTag);
        }
    }

    private BidRequest decodeRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return codec.decodeRequest(body);
    }

    private static String mediaTypeOf(BidRequest bidRequest) {
        if (bidRequest == null || bidRequest.imp() == null || bidRequest.imp().isEmpty()) {
            return RtbMetricTags.UNKNOWN;
        }

        Imp imp = bidRequest.imp().getFirst();
        if (imp.banner() != null) {
            return "banner";
        }
        if (imp.video() != null) {
            return "video";
        }
        if (imp.nativeAd() != null) {
            return "native";
        }
        return RtbMetricTags.UNKNOWN;
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
