package com.bbororo.rtb.dsp.bidbuilder;

import com.bbororo.rtb.dsp.pricing.BidDecision;
import com.bbororo.rtb.shared.openrtb.BidResponse;

public interface BidBuilder {

    BidResponse build(BidDecision decision);
}
