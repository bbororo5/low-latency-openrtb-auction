package com.bbororo.rtb.ssp.contract;

import com.bbororo.rtb.shared.common.AuctionType;
import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.openrtb.Bid;
import com.bbororo.rtb.shared.openrtb.BidResponse;
import com.bbororo.rtb.shared.openrtb.SeatBid;
import com.bbororo.rtb.ssp.auctionflow.AuctionRequest;
import com.bbororo.rtb.ssp.auctionflow.Deadline;
import com.bbororo.rtb.ssp.bidjudge.BidJudge;
import com.bbororo.rtb.ssp.bidjudge.DefaultBidJudge;
import com.bbororo.rtb.ssp.dspgateway.DspCallResult;
import com.bbororo.rtb.ssp.dspgateway.DspCallStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SspDspGatewayToBidJudgeContractTest {

    private final BidJudge bidJudge = new DefaultBidJudge();

    @Test
    void dsp_gateway_returns_classified_dsp_call_results_for_bid_judgement() {
        var request = new AuctionRequest(
                "auction-1",
                "imp-1",
                MediaType.BANNER,
                new BigDecimal("1.00"),
                "USD",
                80,
                AuctionType.FIRST_PRICE,
                Instant.parse("2026-06-26T10:15:30Z")
        );
        var deadline = new Deadline(Instant.parse("2026-06-26T10:15:30.080Z"));
        var results = List.of(
                bidReceived("dsp-valid", "bid-valid", "imp-1", "1.20", Instant.parse("2026-06-26T10:15:30.020Z")),
                bidReceived("dsp-under-floor", "bid-low", "imp-1", "0.70", Instant.parse("2026-06-26T10:15:30.021Z")),
                new DspCallResult("dsp-no-bid", DspCallStatus.NO_BID, null, Instant.parse("2026-06-26T10:15:30.015Z")),
                new DspCallResult("dsp-timeout", DspCallStatus.TIMEOUT, null, Instant.parse("2026-06-26T10:15:30.081Z"))
        );

        var judgement = bidJudge.judge(request, results, deadline);

        assertAll(
                () -> assertEquals(1, judgement.validCandidates().size()),
                () -> assertEquals("dsp-valid", judgement.validCandidates().getFirst().dspId()),
                () -> assertEquals("bid-valid", judgement.validCandidates().getFirst().bid().id()),
                () -> assertEquals(2, judgement.summary().bidCount()),
                () -> assertEquals(1, judgement.summary().noBidCount()),
                () -> assertEquals(1, judgement.summary().timeoutCount()),
                () -> assertEquals(1, judgement.summary().invalidBidCount())
        );
    }

    private static DspCallResult bidReceived(String dspId, String bidId, String impId, String price, Instant receivedAt) {
        var bid = new Bid(
                bidId,
                impId,
                new BigDecimal(price),
                "campaign-1",
                "creative-1",
                List.of("advertiser.example"),
                1,
                "<div>ad</div>"
        );
        var response = new BidResponse("auction-1", List.of(new SeatBid(dspId, List.of(bid))), "USD");
        return new DspCallResult(dspId, DspCallStatus.BID_RECEIVED, response, receivedAt);
    }
}
