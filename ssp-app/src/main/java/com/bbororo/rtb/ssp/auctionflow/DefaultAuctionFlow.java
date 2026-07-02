package com.bbororo.rtb.ssp.auctionflow;

import com.bbororo.rtb.ssp.bidjudge.BidJudge;
import com.bbororo.rtb.ssp.bidjudge.JudgementResult;
import com.bbororo.rtb.ssp.bidjudge.JudgementSummary;
import com.bbororo.rtb.ssp.dspgateway.DspCallResult;
import com.bbororo.rtb.ssp.dspgateway.DspGateway;
import com.bbororo.rtb.ssp.winnerselector.AuctionOutcome;
import com.bbororo.rtb.ssp.winnerselector.AuctionResult;
import com.bbororo.rtb.ssp.winnerselector.AuctionResultStatus;
import com.bbororo.rtb.ssp.winnerselector.WinnerSelector;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class DefaultAuctionFlow implements AuctionFlow {

    private static final JudgementSummary EMPTY_SUMMARY = new JudgementSummary(0, 0, 0, 0, 0, 0);

    private final AuctionDeadlinePolicy deadlinePolicy;
    private final DspGateway dspGateway;
    private final BidJudge bidJudge;
    private final WinnerSelector winnerSelector;

    public DefaultAuctionFlow(
            AuctionDeadlinePolicy deadlinePolicy,
            DspGateway dspGateway,
            BidJudge bidJudge,
            WinnerSelector winnerSelector
    ) {
        this.deadlinePolicy = Objects.requireNonNull(deadlinePolicy, "deadlinePolicy must not be null");
        this.dspGateway = Objects.requireNonNull(dspGateway, "dspGateway must not be null");
        this.bidJudge = Objects.requireNonNull(bidJudge, "bidJudge must not be null");
        this.winnerSelector = Objects.requireNonNull(winnerSelector, "winnerSelector must not be null");
    }

    @Override
    public AuctionResult run(AuctionCommand command) {
        if (command == null) {
            return invalidResult();
        }

        AuctionRequest request = command.auctionRequest();
        Deadline deadline = deadlinePolicy.calculate(request);
        List<DspCallResult> dspResults = dspGateway.requestBids(command.bidRequest(), deadline);
        JudgementResult judgement = bidJudge.judge(request, dspResults, deadline);
        AuctionOutcome outcome = winnerSelector.select(judgement.validCandidates());

        return toAuctionResult(request, judgement, outcome);
    }

    private static AuctionResult invalidResult() {
        return new AuctionResult(
                null,
                null,
                null,
                AuctionResultStatus.INVALID_REQUEST,
                null,
                null,
                null,
                null,
                null,
                0,
                EMPTY_SUMMARY
        );
    }

    private static AuctionResult toAuctionResult(
            AuctionRequest request,
            JudgementResult judgement,
            AuctionOutcome outcome
    ) {
        long elapsedMs = elapsedMs(request.receivedAt());
        return outcome.winner()
                .map(winner -> new AuctionResult(
                        request.requestId(),
                        request.impId(),
                        request.mediaType(),
                        AuctionResultStatus.WINNER,
                        winner.dspId(),
                        winner.bid().id(),
                        winner.bid().price(),
                        auctionPrice(winner.bid().price()),
                        request.bidfloorcur(),
                        elapsedMs,
                        judgement.summary()
                ))
                .orElseGet(() -> new AuctionResult(
                        request.requestId(),
                        request.impId(),
                        request.mediaType(),
                        AuctionResultStatus.NO_WINNER,
                        null,
                        null,
                        null,
                        null,
                        request.bidfloorcur(),
                        elapsedMs,
                        judgement.summary()
                ));
    }

    private static BigDecimal auctionPrice(BigDecimal winningPrice) {
        return winningPrice;
    }

    private static long elapsedMs(Instant receivedAt) {
        return Duration.between(receivedAt, Instant.now()).toMillis();
    }
}
