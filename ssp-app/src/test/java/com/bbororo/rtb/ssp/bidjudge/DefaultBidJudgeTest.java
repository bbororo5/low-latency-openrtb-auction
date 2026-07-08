package com.bbororo.rtb.ssp.bidjudge;

import com.bbororo.rtb.shared.common.AuctionType;
import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.openrtb.Bid;
import com.bbororo.rtb.shared.openrtb.BidResponse;
import com.bbororo.rtb.shared.openrtb.SeatBid;
import com.bbororo.rtb.ssp.auctionflow.AuctionRequest;
import com.bbororo.rtb.ssp.auctionflow.Deadline;
import com.bbororo.rtb.ssp.dspgateway.DspCallResult;
import com.bbororo.rtb.ssp.dspgateway.DspCallStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultBidJudgeTest {

    @Test
    void accepts_bid_matching_request_id_imp_currency_floor_and_media_type() {
        DefaultBidJudge judge = new DefaultBidJudge();

        JudgementResult result = judge.judge(
                request(MediaType.BANNER),
                List.of(bidResult(response("req-001", "USD", bid("imp-001", "1.20", 1, null)))),
                new Deadline(Instant.EPOCH.plusMillis(100))
        );

        assertEquals(1, result.validCandidates().size());
        assertEquals(1, result.summary().bidCount());
        assertEquals(0, result.summary().invalidBidCount());
    }

    @Test
    void rejects_response_with_mismatched_request_id() {
        DefaultBidJudge judge = new DefaultBidJudge();

        JudgementResult result = judge.judge(
                request(MediaType.BANNER),
                List.of(bidResult(response("other-req", "USD", bid("imp-001", "1.20", 1, null)))),
                new Deadline(Instant.EPOCH.plusMillis(100))
        );

        assertEquals(0, result.validCandidates().size());
        assertEquals(1, result.summary().invalidBidCount());
    }

    @Test
    void rejects_response_with_mismatched_currency() {
        DefaultBidJudge judge = new DefaultBidJudge();

        JudgementResult result = judge.judge(
                request(MediaType.BANNER),
                List.of(bidResult(response("req-001", "KRW", bid("imp-001", "1.20", 1, null)))),
                new Deadline(Instant.EPOCH.plusMillis(100))
        );

        assertEquals(0, result.validCandidates().size());
        assertEquals(1, result.summary().invalidBidCount());
    }

    @Test
    void rejects_bid_with_mismatched_media_type() {
        DefaultBidJudge judge = new DefaultBidJudge();

        JudgementResult result = judge.judge(
                request(MediaType.BANNER),
                List.of(bidResult(response("req-001", "USD", bid("imp-001", "1.20", 2, "<VAST></VAST>")))),
                new Deadline(Instant.EPOCH.plusMillis(100))
        );

        assertEquals(0, result.validCandidates().size());
        assertEquals(1, result.summary().invalidBidCount());
    }

    @Test
    void rejects_video_bid_without_ad_markup() {
        DefaultBidJudge judge = new DefaultBidJudge();

        JudgementResult result = judge.judge(
                request(MediaType.VIDEO),
                List.of(bidResult(response("req-001", "USD", bid("imp-001", "1.20", 2, null)))),
                new Deadline(Instant.EPOCH.plusMillis(100))
        );

        assertEquals(0, result.validCandidates().size());
        assertEquals(1, result.summary().invalidBidCount());
    }

    private static AuctionRequest request(MediaType mediaType) {
        return new AuctionRequest(
                "req-001",
                "imp-001",
                mediaType,
                new BigDecimal("0.50"),
                "USD",
                120,
                AuctionType.FIRST_PRICE,
                Instant.EPOCH
        );
    }

    private static DspCallResult bidResult(BidResponse bidResponse) {
        return new DspCallResult("dsp-a", DspCallStatus.BID_RECEIVED, bidResponse, Instant.EPOCH.plusMillis(10));
    }

    private static BidResponse response(String requestId, String currency, Bid bid) {
        return new BidResponse(requestId, List.of(new SeatBid("dsp-a", List.of(bid))), currency);
    }

    private static Bid bid(String impId, String price, int mediaType, String adm) {
        return new Bid(
                "bid-001",
                impId,
                new BigDecimal(price),
                "campaign-001",
                "creative-001",
                List.of("advertiser.example"),
                mediaType,
                adm
        );
    }
}
