package com.bbororo.rtb.dsp.bidbuilder;

import com.bbororo.rtb.dsp.pricing.PricedBid;
import com.bbororo.rtb.shared.openrtb.BidResponse;

public interface BidBuilder {

    BidResponse build(PricedBid pricedBid);
}
