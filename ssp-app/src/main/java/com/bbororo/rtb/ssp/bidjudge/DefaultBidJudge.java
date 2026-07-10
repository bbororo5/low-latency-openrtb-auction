package com.bbororo.rtb.ssp.bidjudge;

import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.openrtb.Bid;
import com.bbororo.rtb.shared.openrtb.BidResponse;
import com.bbororo.rtb.shared.openrtb.SeatBid;
import com.bbororo.rtb.ssp.auctionflow.AuctionRequest;
import com.bbororo.rtb.ssp.auctionflow.Deadline;
import com.bbororo.rtb.ssp.dspgateway.DspCallResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultBidJudge implements BidJudge {

    private static final String UNKNOWN_DSP = "unknown";

    private final DspTerminalResultObserver terminalResultObserver;

    public DefaultBidJudge() {
        this((dspId, terminalResult) -> {
        });
    }

    public DefaultBidJudge(DspTerminalResultObserver terminalResultObserver) {
        this.terminalResultObserver = Objects.requireNonNull(
                terminalResultObserver,
                "terminalResultObserver must not be null"
        );
    }

    @Override
    public JudgementResult judge(AuctionRequest request, List<DspCallResult> results, Deadline deadline) {
        var validCandidates = new ArrayList<ValidBidCandidate>();
        var summary = new MutableSummary();

        if (results == null) {
            return new JudgementResult(List.of(), summary.toImmutable());
        }

        for (DspCallResult result : results) {
            DspTerminalResult terminalResult = classify(request, deadline, result, validCandidates);
            summary.increment(terminalResult);
            terminalResultObserver.record(dspId(result), terminalResult);
        }

        return new JudgementResult(List.copyOf(validCandidates), summary.toImmutable());
    }

    private static DspTerminalResult classify(
            AuctionRequest request,
            Deadline deadline,
            DspCallResult result,
            List<ValidBidCandidate> validCandidates
    ) {
        if (result == null || result.status() == null) {
            return DspTerminalResult.ERROR;
        }

        return switch (result.status()) {
            case NO_BID -> DspTerminalResult.NO_BID;
            case TIMEOUT -> DspTerminalResult.TIMEOUT;
            case ERROR -> DspTerminalResult.ERROR;
            case INVALID_RESPONSE -> DspTerminalResult.INVALID_BID;
            case BID_RECEIVED -> classifyBidResponse(request, deadline, result, validCandidates);
        };
    }

    private static DspTerminalResult classifyBidResponse(
            AuctionRequest request,
            Deadline deadline,
            DspCallResult result,
            List<ValidBidCandidate> validCandidates
    ) {
        if (deadline != null && result.receivedAt() != null && result.receivedAt().isAfter(deadline.value())) {
            return DspTerminalResult.TIMEOUT;
        }

        BidResponse bidResponse = result.bidResponse();
        if (bidResponse == null
                || !request.requestId().equals(bidResponse.id())
                || unsupportedCurrency(request, bidResponse)
                || bidResponse.seatbid() == null
                || bidResponse.seatbid().isEmpty()) {
            return DspTerminalResult.INVALID_BID;
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

        return acceptedAnyBid ? DspTerminalResult.VALID_BID : DspTerminalResult.INVALID_BID;
    }

    private static boolean isValidBid(AuctionRequest request, Bid bid) {
        return bid != null
                && bid.id() != null
                && !bid.id().isBlank()
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

    private static String dspId(DspCallResult result) {
        if (result == null || result.dspId() == null || result.dspId().isBlank()) {
            return UNKNOWN_DSP;
        }
        return result.dspId();
    }

    private static final class MutableSummary {
        private int validBidCount;
        private int noBidCount;
        private int timeoutCount;
        private int invalidBidCount;
        private int errorCount;

        private void increment(DspTerminalResult result) {
            switch (result) {
                case VALID_BID -> validBidCount++;
                case NO_BID -> noBidCount++;
                case TIMEOUT -> timeoutCount++;
                case INVALID_BID -> invalidBidCount++;
                case ERROR -> errorCount++;
            }
        }

        private JudgementSummary toImmutable() {
            return new JudgementSummary(
                    validBidCount,
                    noBidCount,
                    timeoutCount,
                    invalidBidCount,
                    errorCount
            );
        }
    }
}
