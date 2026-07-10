package com.bbororo.rtb.ssp.bidjudge;

public record JudgementSummary(
        int validBidCount,
        int noBidCount,
        int timeoutCount,
        int invalidBidCount,
        int errorCount
) {

    public int totalCount() {
        return validBidCount + noBidCount + timeoutCount + invalidBidCount + errorCount;
    }
}
