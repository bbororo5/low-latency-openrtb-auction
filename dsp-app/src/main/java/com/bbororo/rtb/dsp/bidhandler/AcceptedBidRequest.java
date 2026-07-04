package com.bbororo.rtb.dsp.bidhandler;

import com.bbororo.rtb.dsp.campaignlookup.BidContext;

public record AcceptedBidRequest(BidContext bidContext) implements BidHandlingResult {
}
