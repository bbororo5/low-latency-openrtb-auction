package com.bbororo.rtb.shared.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class RtbMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger sspDspInflightCalls;

    public RtbMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.sspDspInflightCalls = registry.gauge(
                "rtb_ssp_dsp_inflight_calls",
                new AtomicInteger(0)
        );
    }

    public void recordSspAuction(Duration duration, String mediaType, String result) {
        timer(
                "rtb_ssp_auction_duration",
                "media_type", RtbMetricTags.value(mediaType),
                "result", RtbMetricTags.value(result)
        ).record(duration);
        registry.counter(
                "rtb_ssp_auction_result_total",
                "media_type", RtbMetricTags.value(mediaType),
                "result", RtbMetricTags.value(result)
        ).increment();
    }

    public void recordSspDspCall(Duration duration, String dspId, String result) {
        timer(
                "rtb_ssp_dsp_call_duration",
                "dsp_id", RtbMetricTags.value(dspId),
                "result", RtbMetricTags.value(result)
        ).record(duration);
        registry.counter(
                "rtb_ssp_dsp_call_result_total",
                "dsp_id", RtbMetricTags.value(dspId),
                "result", RtbMetricTags.value(result)
        ).increment();
    }

    public void incrementSspDspInflightCalls() {
        sspDspInflightCalls.incrementAndGet();
    }

    public void decrementSspDspInflightCalls() {
        sspDspInflightCalls.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void recordDspBidHandling(Duration duration, String mediaType, String result) {
        timer(
                "rtb_dsp_bid_handle_duration",
                "media_type", RtbMetricTags.value(mediaType),
                "result", RtbMetricTags.value(result)
        ).record(duration);
        registry.counter(
                "rtb_dsp_bid_result_total",
                "media_type", RtbMetricTags.value(mediaType),
                "result", RtbMetricTags.value(result)
        ).increment();
    }

    public void recordDspNoBidReason(String mediaType, String reason) {
        registry.counter(
                "rtb_dsp_no_bid_reason_total",
                "media_type", RtbMetricTags.value(mediaType),
                "reason", RtbMetricTags.value(reason)
        ).increment();
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name)
                .tags(tags)
                .publishPercentileHistogram()
                .register(registry);
    }
}
