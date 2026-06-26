package com.bbororo.rtb.ssp.winnerselector;

import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.ssp.bidjudge.JudgementSummary;

import java.math.BigDecimal;

public record AuctionResult(
        String requestId,
        String impId,
        MediaType mediaType,
        AuctionResultStatus status,
        String winnerDspId,
        String winningBidId,
        BigDecimal winningPrice,
        BigDecimal auctionPrice,
        String currency,
        long elapsedMs,
        JudgementSummary dspResultCounts
) {
}
