package com.bbororo.rtb.dsp.bidhandler;

import com.bbororo.rtb.shared.openrtb.BidRequest;

public interface BidHandler {

    BidHandlingResult handle(BidRequest bidRequest);
}
