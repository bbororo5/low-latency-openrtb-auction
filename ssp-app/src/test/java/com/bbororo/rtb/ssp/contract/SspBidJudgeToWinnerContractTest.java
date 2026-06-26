package com.bbororo.rtb.ssp.contract;

import com.bbororo.rtb.shared.openrtb.Bid;
import com.bbororo.rtb.ssp.bidjudge.ValidBidCandidate;
import com.bbororo.rtb.ssp.winnerselector.FirstPriceWinnerSelector;
import com.bbororo.rtb.ssp.winnerselector.WinnerSelector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SspBidJudgeToWinnerContractTest {

    private final WinnerSelector winnerSelector = new FirstPriceWinnerSelector();

    @Test
    void bid_judge_passes_only_valid_bid_candidates_to_winner_selector() {
        var lowBid = candidate("dsp-low", "bid-low", "1.10");
        var highBid = candidate("dsp-high", "bid-high", "1.35");

        var outcome = winnerSelector.select(List.of(lowBid, highBid));

        assertTrue(outcome.winner().isPresent());
        assertEquals("dsp-high", outcome.winner().orElseThrow().dspId());
        assertEquals("bid-high", outcome.winner().orElseThrow().bid().id());
    }

    @Test
    void winner_selector_returns_empty_outcome_when_no_valid_bid_exists() {
        var outcome = winnerSelector.select(List.of());

        assertTrue(outcome.winner().isEmpty());
    }

    private static ValidBidCandidate candidate(String dspId, String bidId, String price) {
        return new ValidBidCandidate(
                dspId,
                new Bid(
                        bidId,
                        "imp-1",
                        new BigDecimal(price),
                        "campaign-1",
                        "creative-1",
                        List.of("advertiser.example"),
                        1,
                        "<div>ad</div>"
                ),
                Instant.parse("2026-06-26T10:15:30Z")
        );
    }
}
