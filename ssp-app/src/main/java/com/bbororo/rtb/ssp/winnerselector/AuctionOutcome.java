package com.bbororo.rtb.ssp.winnerselector;

import com.bbororo.rtb.ssp.bidjudge.ValidBidCandidate;

import java.util.Optional;

public record AuctionOutcome(Optional<ValidBidCandidate> winner) {
}
