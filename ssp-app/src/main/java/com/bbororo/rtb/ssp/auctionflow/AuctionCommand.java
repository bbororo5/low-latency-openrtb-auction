package com.bbororo.rtb.ssp.auctionflow;

import com.bbororo.rtb.shared.openrtb.BidRequest;

import java.util.Objects;

public record AuctionCommand(
        BidRequest bidRequest,
        AuctionRequest auctionRequest
) {

    public AuctionCommand {
        Objects.requireNonNull(bidRequest, "bidRequest must not be null");
        Objects.requireNonNull(auctionRequest, "auctionRequest must not be null");
    }
}
