package com.bbororo.rtb.dsp.campaignlookup;

import java.math.BigDecimal;

public record BidPolicy(
        BigDecimal baseBidCpm,
        BigDecimal maxBidCpm,
        String currency
) {
}
