package com.bbororo.rtb.dsp.campaignlookup;

import com.bbororo.rtb.shared.common.AuctionType;
import com.bbororo.rtb.shared.common.MediaType;

import java.math.BigDecimal;

public record BidContext(
        String requestId,
        String impId,
        MediaType mediaType,
        BigDecimal bidfloor,
        String currency,
        AuctionType auctionType,
        Integer tmax
) {
}
