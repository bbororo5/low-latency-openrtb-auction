package com.bbororo.rtb.ssp.dspgateway;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DspHttpExecutorConfigTest {

    @Test
    void uses_defaults_when_env_values_are_missing() {
        DspHttpExecutorConfig config = DspHttpExecutorConfig.from(Map.of());

        assertEquals(64, config.corePoolSize());
        assertEquals(128, config.maxPoolSize());
        assertEquals(512, config.queueCapacity());
        assertEquals(Duration.ofSeconds(30), config.keepAlive());
    }

    @Test
    void reads_executor_limits_from_env_values() {
        DspHttpExecutorConfig config = DspHttpExecutorConfig.from(Map.of(
                "DSP_HTTP_EXECUTOR_CORE_SIZE", "8",
                "DSP_HTTP_EXECUTOR_MAX_SIZE", "16",
                "DSP_HTTP_EXECUTOR_QUEUE_CAPACITY", "32",
                "DSP_HTTP_EXECUTOR_KEEP_ALIVE_MILLIS", "1000"
        ));

        assertEquals(8, config.corePoolSize());
        assertEquals(16, config.maxPoolSize());
        assertEquals(32, config.queueCapacity());
        assertEquals(Duration.ofSeconds(1), config.keepAlive());
    }

    @Test
    void rejects_invalid_pool_bounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspHttpExecutorConfig(16, 8, 32, Duration.ofSeconds(1))
        );
    }
}
