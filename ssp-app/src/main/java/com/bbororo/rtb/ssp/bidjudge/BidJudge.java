package com.bbororo.rtb.ssp.bidjudge;

import com.bbororo.rtb.ssp.auctionflow.AuctionRequest;
import com.bbororo.rtb.ssp.auctionflow.Deadline;
import com.bbororo.rtb.ssp.dspgateway.DspCallResult;

import java.util.List;

public interface BidJudge {

    JudgementResult judge(AuctionRequest request, List<DspCallResult> results, Deadline deadline);
}
