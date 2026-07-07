package com.bbororo.rtb.shared.observability;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.ToDoubleFunction;

public final class ExecutorMetrics {

    private ExecutorMetrics() {
    }

    public static void bindSspDspExecutor(
            MeterRegistry registry,
            ThreadPoolExecutor executor,
            ToDoubleFunction<ThreadPoolExecutor> rejectedTaskCount
    ) {
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(rejectedTaskCount, "rejectedTaskCount must not be null");

        Gauge.builder("rtb_ssp_dsp_executor_active_threads", executor, ThreadPoolExecutor::getActiveCount)
                .register(registry);
        Gauge.builder("rtb_ssp_dsp_executor_pool_size", executor, ThreadPoolExecutor::getPoolSize)
                .register(registry);
        Gauge.builder("rtb_ssp_dsp_executor_queued_tasks", executor, value -> value.getQueue().size())
                .register(registry);
        FunctionCounter.builder(
                        "rtb_ssp_dsp_executor_completed_tasks",
                        executor,
                        ThreadPoolExecutor::getCompletedTaskCount
                )
                .register(registry);
        FunctionCounter.builder(
                        "rtb_ssp_dsp_executor_rejected_tasks",
                        executor,
                        rejectedTaskCount
                )
                .register(registry);
    }
}
