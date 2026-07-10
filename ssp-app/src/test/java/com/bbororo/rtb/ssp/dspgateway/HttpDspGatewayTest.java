package com.bbororo.rtb.ssp.dspgateway;

import com.bbororo.rtb.shared.openrtb.Banner;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.Imp;
import com.bbororo.rtb.shared.openrtb.codec.JacksonOpenRtbJsonCodec;
import com.bbororo.rtb.shared.observability.RtbMetrics;
import com.bbororo.rtb.ssp.auctionflow.Deadline;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpDspGatewayTest {

    @Test
    void sends_remaining_deadline_budget_as_openrtb_tmax() {
        JacksonOpenRtbJsonCodec codec = new JacksonOpenRtbJsonCodec();
        AtomicInteger observedTmax = new AtomicInteger();
        DspEndpoint endpoint = new DspEndpoint("dsp-a", URI.create("http://localhost/openrtb/bid"));
        HttpDspClient client = (target, jsonBody, timeout) -> {
            observedTmax.set(codec.decodeRequest(jsonBody).tmax());
            return CompletableFuture.completedFuture(new DspHttpResponse(target, 204, "", Instant.now()));
        };
        HttpDspGateway gateway = new HttpDspGateway(
                new StaticDspEndpointRegistry(List.of(endpoint)),
                codec,
                client,
                new DspHttpResultMapper(codec),
                new RtbMetrics(new SimpleMeterRegistry())
        );

        List<DspCallResult> results = gateway.requestBids(request(120), new Deadline(Instant.now().plusMillis(80)));

        assertEquals(1, results.size());
        assertEquals(DspCallStatus.NO_BID, results.getFirst().status());
        assertTrue(observedTmax.get() > 0);
        assertTrue(observedTmax.get() <= 80);
    }

    @Test
    void maps_a_response_observed_after_cutoff_to_timeout() {
        JacksonOpenRtbJsonCodec codec = new JacksonOpenRtbJsonCodec();
        DspEndpoint endpoint = new DspEndpoint("dsp-a", URI.create("http://localhost/openrtb/bid"));
        DspHttpResultMapper mapper = new DspHttpResultMapper(codec);

        DspCallResult result = mapper.fromResponse(
                new DspHttpResponse(endpoint, 204, "", Instant.EPOCH.plusMillis(101)),
                new Deadline(Instant.EPOCH.plusMillis(100))
        );

        assertEquals(DspCallStatus.TIMEOUT, result.status());
    }

    @Test
    void does_not_start_dsp_calls_after_the_global_deadline() {
        JacksonOpenRtbJsonCodec codec = new JacksonOpenRtbJsonCodec();
        AtomicInteger callCount = new AtomicInteger();
        DspEndpoint endpoint = new DspEndpoint("dsp-a", URI.create("http://localhost/openrtb/bid"));
        HttpDspClient client = (target, jsonBody, timeout) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(new DspHttpResponse(target, 204, "", Instant.now()));
        };
        HttpDspGateway gateway = new HttpDspGateway(
                new StaticDspEndpointRegistry(List.of(endpoint)),
                codec,
                client,
                new DspHttpResultMapper(codec),
                new RtbMetrics(new SimpleMeterRegistry())
        );

        List<DspCallResult> results = gateway.requestBids(request(120), new Deadline(Instant.now().minusMillis(1)));

        assertEquals(0, callCount.get());
        assertEquals(List.of(), results);
    }

    private static BidRequest request(int tmax) {
        return new BidRequest(
                "req-001",
                List.of(new Imp(
                        "imp-001",
                        new Banner(300, 250),
                        null,
                        null,
                        new BigDecimal("0.50"),
                        "USD"
                )),
                tmax,
                1
        );
    }
}
