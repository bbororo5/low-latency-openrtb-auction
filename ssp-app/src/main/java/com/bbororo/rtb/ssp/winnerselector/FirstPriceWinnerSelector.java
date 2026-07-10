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
            if (isPreferred(candidate, winner)) {
                winner = candidate;
            }
        }
        return new AuctionOutcome(Optional.of(winner));
    }

    private static boolean isPreferred(ValidBidCandidate candidate, ValidBidCandidate winner) {
        int priceOrder = candidate.bid().price().compareTo(winner.bid().price());
        if (priceOrder != 0) {
            return priceOrder > 0;
        }

        int dspOrder = candidate.dspId().compareTo(winner.dspId());
        if (dspOrder != 0) {
            return dspOrder < 0;
        }
        return candidate.bid().id().compareTo(winner.bid().id()) < 0;
    }
}
