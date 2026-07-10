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
import java.util.ArrayList;
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
        assertEquals(1, result.summary().validBidCount());
        assertEquals(0, result.summary().invalidBidCount());
        assertEquals(1, result.summary().totalCount());
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
        assertEquals(1, result.summary().totalCount());
    }

    @Test
    void assigns_exactly_one_terminal_result_to_each_dsp_call() {
        List<String> observed = new ArrayList<>();
        DefaultBidJudge judge = new DefaultBidJudge(
                (dspId, terminalResult) -> observed.add(dspId + ":" + terminalResult.name())
        );
        Instant receivedAt = Instant.EPOCH.plusMillis(10);

        JudgementResult result = judge.judge(
                request(MediaType.BANNER),
                List.of(
                        bidResult(response("req-001", "USD", bid("imp-001", "1.20", 1, null))),
                        new DspCallResult("dsp-b", DspCallStatus.NO_BID, null, receivedAt),
                        new DspCallResult("dsp-c", DspCallStatus.TIMEOUT, null, receivedAt),
                        new DspCallResult("dsp-d", DspCallStatus.INVALID_RESPONSE, null, receivedAt),
                        new DspCallResult("dsp-e", DspCallStatus.ERROR, null, receivedAt)
                ),
                new Deadline(Instant.EPOCH.plusMillis(100))
        );

        assertEquals(1, result.summary().validBidCount());
        assertEquals(1, result.summary().noBidCount());
        assertEquals(1, result.summary().timeoutCount());
        assertEquals(1, result.summary().invalidBidCount());
        assertEquals(1, result.summary().errorCount());
        assertEquals(5, result.summary().totalCount());
        assertEquals(List.of(
                "dsp-a:VALID_BID",
                "dsp-b:NO_BID",
                "dsp-c:TIMEOUT",
                "dsp-d:INVALID_BID",
                "dsp-e:ERROR"
        ), observed);
    }

    @Test
    void classifies_a_bid_received_after_cutoff_as_timeout_only() {
        DefaultBidJudge judge = new DefaultBidJudge();
        DspCallResult late = new DspCallResult(
                "dsp-a",
                DspCallStatus.BID_RECEIVED,
                response("req-001", "USD", bid("imp-001", "9.99", 1, null)),
                Instant.EPOCH.plusMillis(101)
        );

        JudgementResult result = judge.judge(
                request(MediaType.BANNER),
                List.of(late),
                new Deadline(Instant.EPOCH.plusMillis(100))
        );

        assertEquals(0, result.validCandidates().size());
        assertEquals(1, result.summary().timeoutCount());
        assertEquals(1, result.summary().totalCount());
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
