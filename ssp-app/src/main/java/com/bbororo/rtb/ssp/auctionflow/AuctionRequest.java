package com.bbororo.rtb.ssp.auctionflow;

import com.bbororo.rtb.shared.common.AuctionType;
import com.bbororo.rtb.shared.common.MediaType;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionRequest(
        String requestId,
        String impId,
        MediaType mediaType,
        BigDecimal bidfloor,
        String bidfloorcur,
        Integer tmax,
        AuctionType auctionType,
        Instant receivedAt
) {
}
