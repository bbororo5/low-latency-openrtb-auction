package com.bbororo.rtb.ssp.adapter.web;

import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.Imp;
import com.bbororo.rtb.shared.observability.RtbMetricTags;
import com.bbororo.rtb.shared.observability.RtbMetrics;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodec;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodecException;
import com.bbororo.rtb.ssp.auctionflow.AuctionCommand;
import com.bbororo.rtb.ssp.auctionflow.AuctionFlow;
import com.bbororo.rtb.ssp.bidjudge.JudgementSummary;
import com.bbororo.rtb.ssp.requesthandler.AcceptedAuctionRequest;
import com.bbororo.rtb.ssp.requesthandler.RejectedAuctionRequest;
import com.bbororo.rtb.ssp.requesthandler.RequestHandler;
import com.bbororo.rtb.ssp.requesthandler.RequestHandlingResult;
import com.bbororo.rtb.ssp.requesthandler.RequestRejectionReason;
import com.bbororo.rtb.ssp.winnerselector.AuctionResult;
import com.bbororo.rtb.ssp.winnerselector.AuctionResultStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class OpenRtbAuctionHttpHandler implements HttpHandler {

    private static final String POST = "POST";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final JudgementSummary EMPTY_SUMMARY = new JudgementSummary(0, 0, 0, 0, 0);

    private final OpenRtbJsonCodec codec;
    private final RequestHandler requestHandler;
    private final AuctionFlow auctionFlow;
    private final ObjectMapper objectMapper;
    private final RtbMetrics metrics;

    public OpenRtbAuctionHttpHandler(
            OpenRtbJsonCodec codec,
            RequestHandler requestHandler,
            AuctionFlow auctionFlow,
            RtbMetrics metrics
    ) {
        this(codec, requestHandler, auctionFlow, metrics, new ObjectMapper());
    }

    OpenRtbAuctionHttpHandler(
            OpenRtbJsonCodec codec,
            RequestHandler requestHandler,
            AuctionFlow auctionFlow,
            RtbMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.requestHandler = Objects.requireNonNull(requestHandler, "requestHandler must not be null");
        this.auctionFlow = Objects.requireNonNull(auctionFlow, "auctionFlow must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Instant startedAt = Instant.now();
        String mediaType = RtbMetricTags.UNKNOWN;
        String result = "error";
        try (exchange) {
            if (!POST.equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 405);
                return;
            }

            BidRequest bidRequest = decodeRequest(exchange);
            mediaType = mediaTypeOf(bidRequest);
            RequestHandlingResult handlingResult = requestHandler.handle(bidRequest, startedAt);

            AuctionResult auctionResult = switch (handlingResult) {
                case AcceptedAuctionRequest accepted -> auctionFlow.run(new AuctionCommand(bidRequest, accepted.auctionRequest()));
                case RejectedAuctionRequest rejected -> rejectedResult(rejected);
            };

            result = auctionResult.status().name();
            sendJson(exchange, 200, auctionResult);
        } catch (OpenRtbJsonCodecException e) {
            result = AuctionResultStatus.INVALID_REQUEST.name();
            sendEmpty(exchange, 400);
        } catch (RuntimeException e) {
            sendEmpty(exchange, 500);
        } finally {
            metrics.recordSspAuction(Duration.between(startedAt, Instant.now()), mediaType, result);
        }
    }

    private BidRequest decodeRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return codec.decodeRequest(body);
    }

    private static AuctionResult rejectedResult(RejectedAuctionRequest rejected) {
        AuctionResultStatus status = rejected.reason() == RequestRejectionReason.INVALID_REQUEST
                ? AuctionResultStatus.INVALID_REQUEST
                : AuctionResultStatus.UNSUPPORTED_REQUEST;

        return new AuctionResult(
                null,
                null,
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                0,
                EMPTY_SUMMARY
        );
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

    private void sendJson(HttpExchange exchange, int statusCode, AuctionResult auctionResult) throws IOException {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(auctionResult);
            exchange.getResponseHeaders().set(CONTENT_TYPE, APPLICATION_JSON);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (JsonProcessingException e) {
            sendEmpty(exchange, 500);
        }
    }

    private static void sendEmpty(HttpExchange exchange, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, -1);
    }
}
