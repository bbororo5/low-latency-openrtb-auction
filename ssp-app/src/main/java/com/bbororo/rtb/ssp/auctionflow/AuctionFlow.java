package com.bbororo.rtb.ssp.auctionflow;

import com.bbororo.rtb.ssp.winnerselector.AuctionResult;

public interface AuctionFlow {

    AuctionResult run(AuctionRequest request);
}
