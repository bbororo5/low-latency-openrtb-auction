package com.bbororo.rtb.ssp.dspgateway;

import java.time.Duration;
import java.util.Map;

public record DspHttpExecutorConfig(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        Duration keepAlive
) {

    static final String CORE_SIZE_ENV = "DSP_HTTP_EXECUTOR_CORE_SIZE";
    static final String MAX_SIZE_ENV = "DSP_HTTP_EXECUTOR_MAX_SIZE";
    static final String QUEUE_CAPACITY_ENV = "DSP_HTTP_EXECUTOR_QUEUE_CAPACITY";
    static final String KEEP_ALIVE_MILLIS_ENV = "DSP_HTTP_EXECUTOR_KEEP_ALIVE_MILLIS";

    private static final int DEFAULT_CORE_POOL_SIZE = 64;
    private static final int DEFAULT_MAX_POOL_SIZE = 128;
    private static final int DEFAULT_QUEUE_CAPACITY = 512;
    private static final Duration DEFAULT_KEEP_ALIVE = Duration.ofSeconds(30);

    public DspHttpExecutorConfig {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("corePoolSize must be positive");
        }
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("maxPoolSize must be greater than or equal to corePoolSize");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        if (keepAlive == null || keepAlive.isZero() || keepAlive.isNegative()) {
            throw new IllegalArgumentException("keepAlive must be positive");
        }
    }

    public static DspHttpExecutorConfig fromEnv() {
        return from(System.getenv());
    }

    static DspHttpExecutorConfig from(Map<String, String> values) {
        return new DspHttpExecutorConfig(
                intValue(values, CORE_SIZE_ENV, DEFAULT_CORE_POOL_SIZE),
                intValue(values, MAX_SIZE_ENV, DEFAULT_MAX_POOL_SIZE),
                intValue(values, QUEUE_CAPACITY_ENV, DEFAULT_QUEUE_CAPACITY),
                Duration.ofMillis(longValue(values, KEEP_ALIVE_MILLIS_ENV, DEFAULT_KEEP_ALIVE.toMillis()))
        );
    }

    private static int intValue(Map<String, String> values, String key, int defaultValue) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private static long longValue(Map<String, String> values, String key, long defaultValue) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value.trim());
    }
}
