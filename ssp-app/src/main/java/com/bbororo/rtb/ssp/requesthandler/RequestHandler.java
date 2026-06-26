package com.bbororo.rtb.ssp.requesthandler;

import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.ssp.auctionflow.AuctionRequest;

import java.time.Instant;

public interface RequestHandler {

    AuctionRequest handle(BidRequest bidRequest, Instant receivedAt);
}
