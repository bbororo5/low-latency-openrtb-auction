package com.bbororo.rtb.ssp.requesthandler;

import com.bbororo.rtb.shared.openrtb.BidRequest;

import java.time.Instant;

public interface RequestHandler {

    RequestHandlingResult handle(BidRequest bidRequest, Instant receivedAt);
}
