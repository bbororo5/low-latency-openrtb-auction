package com.bbororo.rtb.ssp.requesthandler;

import com.bbororo.rtb.ssp.auctionflow.AuctionRequest;

public record AcceptedAuctionRequest(AuctionRequest auctionRequest) implements RequestHandlingResult {
}
