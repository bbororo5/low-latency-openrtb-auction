package com.bbororo.rtb.ssp.bidjudge;

import com.bbororo.rtb.shared.openrtb.Bid;
import com.bbororo.rtb.shared.openrtb.BidResponse;
import com.bbororo.rtb.shared.openrtb.SeatBid;
import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.ssp.auctionflow.AuctionRequest;
import com.bbororo.rtb.ssp.auctionflow.Deadline;
import com.bbororo.rtb.ssp.dspgateway.DspCallResult;
import com.bbororo.rtb.ssp.dspgateway.DspCallStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class DefaultBidJudge implements BidJudge {

    @Override
    public JudgementResult judge(AuctionRequest request, List<DspCallResult> results, Deadline deadline) {
        var validCandidates = new ArrayList<ValidBidCandidate>();
        var summary = new MutableSummary();

        if (results == null) {
            return new JudgementResult(List.of(), summary.toImmutable());
        }

        for (DspCallResult result : results) {
            judgeOne(request, deadline, result, validCandidates, summary);
        }

        return new JudgementResult(List.copyOf(validCandidates), summary.toImmutable());
    }

    private static void judgeOne(
            AuctionRequest request,
            Deadline deadline,
            DspCallResult result,
            List<ValidBidCandidate> validCandidates,
            MutableSummary summary
    ) {
        if (result == null || result.status() == null) {
            summary.invalidBidCount++;
            return;
        }

        switch (result.status()) {
            case NO_BID -> summary.noBidCount++;
            case TIMEOUT -> summary.timeoutCount++;
            case ERROR -> summary.errorCount++;
            case LATE_BID -> summary.lateBidCount++;
            case BID_RECEIVED -> judgeBidReceived(request, deadline, result, validCandidates, summary);
        }
    }

    private static void judgeBidReceived(
            AuctionRequest request,
            Deadline deadline,
            DspCallResult result,
            List<ValidBidCandidate> validCandidates,
            MutableSummary summary
    ) {
        summary.bidCount++;
        if (deadline != null && result.receivedAt() != null && result.receivedAt().isAfter(deadline.value())) {
            summary.lateBidCount++;
            return;
        }

        BidResponse bidResponse = result.bidResponse();
        if (bidResponse == null
                || !request.requestId().equals(bidResponse.id())
                || unsupportedCurrency(request, bidResponse)
                || bidResponse.seatbid() == null
                || bidResponse.seatbid().isEmpty()) {
            summary.invalidBidCount++;
            return;
        }

        boolean acceptedAnyBid = false;
        for (SeatBid seatBid : bidResponse.seatbid()) {
            if (seatBid.bid() == null) {
                continue;
            }
            for (Bid bid : seatBid.bid()) {
                if (isValidBid(request, bid)) {
                    validCandidates.add(new ValidBidCandidate(result.dspId(), bid, result.receivedAt()));
                    acceptedAnyBid = true;
                }
            }
        }

        if (!acceptedAnyBid) {
            summary.invalidBidCount++;
        }
    }

    private static boolean isValidBid(AuctionRequest request, Bid bid) {
        return bid != null
                && request.impId().equals(bid.impid())
                && bid.price() != null
                && bid.price().compareTo(BigDecimal.ZERO) > 0
                && bid.price().compareTo(request.bidfloor()) >= 0
                && bid.mtype() != null
                && bid.mtype() == openRtbMediaType(request.mediaType())
                && hasRequiredMarkup(request.mediaType(), bid);
    }

    private static boolean unsupportedCurrency(AuctionRequest request, BidResponse bidResponse) {
        return bidResponse.cur() != null && !bidResponse.cur().equals(request.bidfloorcur());
    }

    private static boolean hasRequiredMarkup(MediaType mediaType, Bid bid) {
        return switch (mediaType) {
            case BANNER -> true;
            case VIDEO, NATIVE -> bid.adm() != null && !bid.adm().isBlank();
        };
    }

    private static int openRtbMediaType(MediaType mediaType) {
        return switch (mediaType) {
            case BANNER -> 1;
            case VIDEO -> 2;
            case NATIVE -> 4;
        };
    }

    private static final class MutableSummary {
        private int bidCount;
        private int noBidCount;
        private int timeoutCount;
        private int lateBidCount;
        private int invalidBidCount;
        private int errorCount;

        private JudgementSummary toImmutable() {
            return new JudgementSummary(
                    bidCount,
                    noBidCount,
                    timeoutCount,
                    lateBidCount,
                    invalidBidCount,
                    errorCount
            );
        }
    }
}
