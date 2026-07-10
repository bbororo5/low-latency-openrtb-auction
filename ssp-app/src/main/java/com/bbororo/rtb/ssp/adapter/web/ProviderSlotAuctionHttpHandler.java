package com.bbororo.rtb.ssp.adapter.web;

import com.bbororo.rtb.shared.observability.RtbMetricTags;
import com.bbororo.rtb.shared.observability.RtbMetrics;
import com.bbororo.rtb.ssp.auctionflow.AuctionFlow;
import com.bbororo.rtb.ssp.slotrequest.AcceptedSlotRequest;
import com.bbororo.rtb.ssp.slotrequest.ProviderSlotRequest;
import com.bbororo.rtb.ssp.slotrequest.RejectedSlotRequest;
import com.bbororo.rtb.ssp.slotrequest.SlotRequestHandler;
import com.bbororo.rtb.ssp.slotrequest.SlotRequestHandlingResult;
import com.bbororo.rtb.ssp.slotrequest.SlotRequestRejectionReason;
import com.bbororo.rtb.ssp.winnerselector.AuctionResult;
import com.bbororo.rtb.ssp.winnerselector.AuctionResultStatus;
import com.bbororo.rtb.ssp.bidjudge.JudgementSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public final class ProviderSlotAuctionHttpHandler implements HttpHandler {

    private static final String POST = "POST";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final JudgementSummary EMPTY_SUMMARY = new JudgementSummary(0, 0, 0, 0, 0);

    private final SlotRequestHandler slotRequestHandler;
    private final AuctionFlow auctionFlow;
    private final RtbMetrics metrics;
    private final ObjectMapper objectMapper;

    public ProviderSlotAuctionHttpHandler(
            SlotRequestHandler slotRequestHandler,
            AuctionFlow auctionFlow,
            RtbMetrics metrics
    ) {
        this(slotRequestHandler, auctionFlow, metrics, new ObjectMapper());
    }

    ProviderSlotAuctionHttpHandler(
            SlotRequestHandler slotRequestHandler,
            AuctionFlow auctionFlow,
            RtbMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.slotRequestHandler = Objects.requireNonNull(slotRequestHandler, "slotRequestHandler must not be null");
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

            ProviderSlotRequest request = decodeRequest(exchange);
            mediaType = mediaTypeTag(request);
            SlotRequestHandlingResult handlingResult = slotRequestHandler.handle(request, startedAt);
            AuctionResult auctionResult = switch (handlingResult) {
                case AcceptedSlotRequest accepted -> auctionFlow.run(accepted.auctionCommand());
                case RejectedSlotRequest rejected -> rejectedResult(rejected);
            };

            result = auctionResult.status().name();
            sendJson(exchange, 200, auctionResult);
        } catch (JsonProcessingException e) {
            result = AuctionResultStatus.INVALID_REQUEST.name();
            sendEmpty(exchange, 400);
        } catch (RuntimeException e) {
            sendEmpty(exchange, 500);
        } finally {
            metrics.recordSspAuction(Duration.between(startedAt, Instant.now()), mediaType, result);
        }
    }

    private ProviderSlotRequest decodeRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return objectMapper.readValue(body, ProviderSlotRequest.class);
    }

    private static AuctionResult rejectedResult(RejectedSlotRequest rejected) {
        AuctionResultStatus status = rejected.reason() == SlotRequestRejectionReason.INVALID_REQUEST
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

    private void sendJson(HttpExchange exchange, int statusCode, AuctionResult auctionResult) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(auctionResult);
        exchange.getResponseHeaders().set(CONTENT_TYPE, APPLICATION_JSON);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sendEmpty(HttpExchange exchange, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, -1);
    }

    private static String mediaTypeTag(ProviderSlotRequest request) {
        if (request == null || request.mediaType() == null || request.mediaType().isBlank()) {
            return RtbMetricTags.UNKNOWN;
        }
        return request.mediaType().trim().toLowerCase(Locale.ROOT);
    }
}
