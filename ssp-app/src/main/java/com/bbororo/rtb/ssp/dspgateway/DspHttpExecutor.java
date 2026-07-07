package com.bbororo.rtb.ssp.dspgateway;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class DspHttpExecutor extends ThreadPoolExecutor {

    private static final String THREAD_NAME_PREFIX = "ssp-dsp-http-";

    private final AtomicLong rejectedTasks = new AtomicLong();

    public DspHttpExecutor(DspHttpExecutorConfig config) {
        super(
                config.corePoolSize(),
                config.maxPoolSize(),
                config.keepAlive().toMillis(),
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.queueCapacity()),
                new NamedThreadFactory(THREAD_NAME_PREFIX),
                new AbortPolicy()
        );
        allowCoreThreadTimeOut(true);
    }

    @Override
    public void execute(Runnable command) {
        try {
            super.execute(command);
        } catch (RejectedExecutionException e) {
            rejectedTasks.incrementAndGet();
            throw e;
        }
    }

    public long rejectedTaskCount() {
        return rejectedTasks.get();
    }

    private static final class NamedThreadFactory implements java.util.concurrent.ThreadFactory {

        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger();

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
