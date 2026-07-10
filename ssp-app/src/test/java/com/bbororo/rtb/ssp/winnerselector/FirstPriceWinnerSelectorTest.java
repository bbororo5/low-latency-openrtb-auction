package com.bbororo.rtb.ssp.winnerselector;

import com.bbororo.rtb.shared.openrtb.Bid;
import com.bbororo.rtb.ssp.bidjudge.ValidBidCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstPriceWinnerSelectorTest {

    @Test
    void chooses_highest_price() {
        AuctionOutcome outcome = new FirstPriceWinnerSelector().select(List.of(
                candidate("dsp-a", "bid-a", "1.20"),
                candidate("dsp-b", "bid-b", "2.10")
        ));

        assertEquals("dsp-b", outcome.winner().orElseThrow().dspId());
    }

    @Test
    void resolves_equal_prices_by_dsp_id_then_bid_id_regardless_of_input_order() {
        List<ValidBidCandidate> candidates = List.of(
                candidate("dsp-b", "bid-a", "2.00"),
                candidate("dsp-a", "bid-z", "2.00"),
                candidate("dsp-a", "bid-a", "2.00")
        );
        FirstPriceWinnerSelector selector = new FirstPriceWinnerSelector();

        for (int seed = 0; seed < 100; seed++) {
            List<ValidBidCandidate> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, new Random(seed));

            ValidBidCandidate winner = selector.select(shuffled).winner().orElseThrow();
            assertEquals("dsp-a", winner.dspId());
            assertEquals("bid-a", winner.bid().id());
        }
    }

    private static ValidBidCandidate candidate(String dspId, String bidId, String price) {
        return new ValidBidCandidate(
                dspId,
                new Bid(
                        bidId,
                        "imp-001",
                        new BigDecimal(price),
                        "campaign-001",
                        "creative-001",
                        List.of("advertiser.example"),
                        1,
                        "<div>ad</div>"
                ),
                Instant.EPOCH
        );
    }
}
