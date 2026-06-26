package com.bbororo.rtb.ssp.bidjudge;

public record JudgementSummary(
        int bidCount,
        int noBidCount,
        int timeoutCount,
        int lateBidCount,
        int invalidBidCount,
        int errorCount
) {
}
