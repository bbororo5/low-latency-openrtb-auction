package com.bbororo.rtb.ssp.auctionflow;

public interface AuctionDeadlinePolicy {

    Deadline calculate(AuctionRequest request);
}
