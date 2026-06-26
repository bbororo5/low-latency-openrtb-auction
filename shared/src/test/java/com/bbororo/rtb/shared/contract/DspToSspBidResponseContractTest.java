package com.bbororo.rtb.shared.contract;

import com.bbororo.rtb.shared.openrtb.Bid;
import com.bbororo.rtb.shared.openrtb.BidResponse;
import com.bbororo.rtb.shared.openrtb.SeatBid;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DspToSspBidResponseContractTest {

    @Test
    void bid_response_contains_fields_required_by_ssp_judgement_and_winner_selection() {
        var bid = new Bid(
                "bid-1",
                "imp-1",
                new BigDecimal("1.25"),
                "campaign-1",
                "creative-1",
                List.of("advertiser.example"),
                1,
                "<div>ad</div>"
        );
        var seatBid = new SeatBid("dsp-seat-1", List.of(bid));
        var bidResponse = new BidResponse("auction-1", List.of(seatBid), "USD");

        assertAll(
                () -> assertEquals("auction-1", bidResponse.id()),
                () -> assertEquals("USD", bidResponse.cur()),
                () -> assertEquals("dsp-seat-1", bidResponse.seatbid().getFirst().seat()),
                () -> assertEquals("bid-1", bidResponse.seatbid().getFirst().bid().getFirst().id()),
                () -> assertEquals("imp-1", bidResponse.seatbid().getFirst().bid().getFirst().impid()),
                () -> assertEquals(new BigDecimal("1.25"), bidResponse.seatbid().getFirst().bid().getFirst().price()),
                () -> assertEquals("campaign-1", bidResponse.seatbid().getFirst().bid().getFirst().cid()),
                () -> assertEquals("creative-1", bidResponse.seatbid().getFirst().bid().getFirst().crid()),
                () -> assertEquals("advertiser.example", bidResponse.seatbid().getFirst().bid().getFirst().adomain().getFirst()),
                () -> assertEquals(1, bidResponse.seatbid().getFirst().bid().getFirst().mtype()),
                () -> assertEquals("<div>ad</div>", bidResponse.seatbid().getFirst().bid().getFirst().adm())
        );
    }
}
