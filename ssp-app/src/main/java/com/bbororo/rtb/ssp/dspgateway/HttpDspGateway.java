package com.bbororo.rtb.ssp.dspgateway;

import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodec;
import com.bbororo.rtb.shared.observability.RtbMetrics;
import com.bbororo.rtb.ssp.auctionflow.Deadline;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HttpDspGateway implements DspGateway {

    private final DspEndpointRegistry endpointRegistry;
    private final OpenRtbJsonCodec codec;
    private final HttpDspClient httpClient;
    private final DspHttpResultMapper resultMapper;
    private final RtbMetrics metrics;

    public HttpDspGateway(
            DspEndpointRegistry endpointRegistry,
            OpenRtbJsonCodec codec,
            HttpDspClient httpClient,
            DspHttpResultMapper resultMapper,
            RtbMetrics metrics
    ) {
        this.endpointRegistry = Objects.requireNonNull(endpointRegistry, "endpointRegistry must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public List<DspCallResult> requestBids(BidRequest bidRequest, Deadline deadline) {
        List<DspEndpoint> endpoints = endpointRegistry.endpoints();
        if (endpoints.isEmpty()) {
            return List.of();
        }
        if (!Instant.now().isBefore(deadline.value())) {
            return List.of();
        }

        String jsonBody = codec.encodeRequest(withRemainingTmax(bidRequest, deadline));
        if (!Instant.now().isBefore(deadline.value())) {
            return List.of();
        }

        List<DspCallFuture> futures = sendRequests(endpoints, jsonBody, deadline);
        waitUntilDeadline(futures, deadline);
        return collectResults(futures);
    }

    private List<DspCallFuture> sendRequests(
            List<DspEndpoint> endpoints,
            String jsonBody,
            Deadline deadline
    ) {
        List<DspCallFuture> futures = new ArrayList<>();
        for (DspEndpoint endpoint : endpoints) {
            if (!Instant.now().isBefore(deadline.value())) {
                break;
            }
            Duration timeout = remaining(deadline);
            Instant startedAt = Instant.now();
            AtomicBoolean inflight = new AtomicBoolean(true);
            metrics.incrementSspDspInflightCalls();
            CompletableFuture<DspCallResult> future;
            try {
                future = httpClient.postBidJson(endpoint, jsonBody, timeout)
                        .thenApply(response -> resultMapper.fromResponse(response, deadline))
                        .exceptionally(error -> resultMapper.fromFailure(endpoint, error, Instant.now()))
                        .whenComplete((result, error) -> releaseInflight(inflight));
            } catch (RuntimeException e) {
                releaseInflight(inflight);
                future = CompletableFuture.completedFuture(resultMapper.fromFailure(endpoint, e, Instant.now()));
            }
            futures.add(new DspCallFuture(endpoint, startedAt, future, inflight));
        }
        return futures;
    }

    private void waitUntilDeadline(List<DspCallFuture> futures, Deadline deadline) {
        CompletableFuture<?>[] pending = futures.stream()
                .map(DspCallFuture::future)
                .toArray(CompletableFuture[]::new);

        try {
            CompletableFuture.allOf(pending).get(remaining(deadline).toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            cancelIncomplete(futures);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelIncomplete(futures);
        } catch (ExecutionException e) {
            cancelIncomplete(futures);
        }
    }

    private List<DspCallResult> collectResults(List<DspCallFuture> futures) {
        List<DspCallResult> results = new ArrayList<>();
        for (DspCallFuture future : futures) {
            if (future.future().isDone() && !future.future().isCancelled()) {
                DspCallResult result = future.future().join();
                metrics.recordSspDspCall(
                        Duration.between(future.startedAt(), result.receivedAt()),
                        result.dspId(),
                        result.status().name()
                );
                results.add(result);
            } else {
                DspCallResult result = resultMapper.fromFailure(future.endpoint(), new TimeoutException(), Instant.now());
                metrics.recordSspDspCall(
                        Duration.between(future.startedAt(), result.receivedAt()),
                        result.dspId(),
                        result.status().name()
                );
                results.add(result);
            }
        }
        return List.copyOf(results);
    }

    private void cancelIncomplete(List<DspCallFuture> futures) {
        for (DspCallFuture future : futures) {
            if (!future.future().isDone()) {
                if (future.future().cancel(true)) {
                    releaseInflight(future.inflight());
                }
            }
        }
    }

    private void releaseInflight(AtomicBoolean inflight) {
        if (inflight.compareAndSet(true, false)) {
            metrics.decrementSspDspInflightCalls();
        }
    }

    private static Duration remaining(Deadline deadline) {
        Duration remaining = Duration.between(Instant.now(), deadline.value());
        if (remaining.isZero() || remaining.isNegative()) {
            return Duration.ofMillis(1);
        }
        return remaining;
    }

    private static BidRequest withRemainingTmax(BidRequest bidRequest, Deadline deadline) {
        long remainingMillis = Math.max(1, remaining(deadline).toMillis());
        int outboundTmax = (int) Math.min(Integer.MAX_VALUE, remainingMillis);
        return new BidRequest(bidRequest.id(), bidRequest.imp(), outboundTmax, bidRequest.at());
    }

    private record DspCallFuture(
            DspEndpoint endpoint,
            Instant startedAt,
            CompletableFuture<DspCallResult> future,
            AtomicBoolean inflight
    ) {
    }
}
