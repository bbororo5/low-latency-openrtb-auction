package com.bbororo.rtb.ssp.winnerselector;

import com.bbororo.rtb.ssp.bidjudge.ValidBidCandidate;

import java.util.List;
import java.util.Optional;

public final class FirstPriceWinnerSelector implements WinnerSelector {

    @Override
    public AuctionOutcome select(List<ValidBidCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new AuctionOutcome(Optional.empty());
        }

        ValidBidCandidate winner = candidates.getFirst();
        for (ValidBidCandidate candidate : candidates) {
            if (candidate.bid().price().compareTo(winner.bid().price()) > 0) {
                winner = candidate;
            }
        }
        return new AuctionOutcome(Optional.of(winner));
    }
}
