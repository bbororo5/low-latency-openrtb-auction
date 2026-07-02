package com.bbororo.rtb.ssp.auctionflow;

public final class DefaultAuctionDeadlinePolicy implements AuctionDeadlinePolicy {

    private static final int FALLBACK_TIMEOUT_MILLIS = 80;

    private final int defaultTimeoutMillis;

    public DefaultAuctionDeadlinePolicy() {
        this(FALLBACK_TIMEOUT_MILLIS);
    }

    public DefaultAuctionDeadlinePolicy(int defaultTimeoutMillis) {
        if (defaultTimeoutMillis <= 0) {
            throw new IllegalArgumentException("defaultTimeoutMillis must be positive");
        }
        this.defaultTimeoutMillis = defaultTimeoutMillis;
    }

    @Override
    public Deadline calculate(AuctionRequest request) {
        int timeoutMillis = request.tmax() == null ? defaultTimeoutMillis : request.tmax();
        if (timeoutMillis <= 0) {
            timeoutMillis = defaultTimeoutMillis;
        }
        return new Deadline(request.receivedAt().plusMillis(timeoutMillis));
    }
}
