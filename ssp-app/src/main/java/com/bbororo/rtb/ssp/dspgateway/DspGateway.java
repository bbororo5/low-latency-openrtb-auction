package com.bbororo.rtb.ssp.dspgateway;

import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.ssp.auctionflow.Deadline;

import java.util.List;

public interface DspGateway {

    List<DspCallResult> requestBids(BidRequest bidRequest, Deadline deadline);
}
