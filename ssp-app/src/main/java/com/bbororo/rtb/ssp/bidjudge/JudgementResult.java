package com.bbororo.rtb.ssp.bidjudge;

import java.util.List;

public record JudgementResult(
        List<ValidBidCandidate> validCandidates,
        JudgementSummary summary
) {
}
