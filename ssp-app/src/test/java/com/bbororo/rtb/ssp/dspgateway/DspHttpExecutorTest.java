package com.bbororo.rtb.ssp.dspgateway;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DspHttpExecutorTest {

    @Test
    void rejects_tasks_when_pool_and_queue_are_full() throws InterruptedException {
        DspHttpExecutor executor = new DspHttpExecutor(
                new DspHttpExecutorConfig(1, 1, 1, Duration.ofMillis(100))
        );
        CountDownLatch release = new CountDownLatch(1);

        try {
            executor.execute(awaitingTask(release));
            executor.execute(() -> {
            });

            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
            }));
            assertEquals(1, executor.rejectedTaskCount());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void names_worker_threads_for_thread_dump_analysis() throws InterruptedException {
        DspHttpExecutor executor = new DspHttpExecutor(
                new DspHttpExecutorConfig(1, 1, 1, Duration.ofMillis(100))
        );
        CountDownLatch done = new CountDownLatch(1);
        String[] threadName = new String[1];

        try {
            executor.execute(() -> {
                threadName[0] = Thread.currentThread().getName();
                done.countDown();
            });

            assertTrue(done.await(1, TimeUnit.SECONDS));
            assertTrue(threadName[0].startsWith("ssp-dsp-http-"));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    private static Runnable awaitingTask(CountDownLatch release) {
        return () -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
