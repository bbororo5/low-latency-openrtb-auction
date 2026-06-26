package com.bbororo.rtb.ssp.winnerselector;

import com.bbororo.rtb.ssp.bidjudge.ValidBidCandidate;

import java.util.List;

public interface WinnerSelector {

    AuctionOutcome select(List<ValidBidCandidate> candidates);
}
