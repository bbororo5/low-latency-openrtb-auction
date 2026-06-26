package com.bbororo.rtb.ssp.bidjudge;

import com.bbororo.rtb.shared.openrtb.Bid;

import java.time.Instant;

public record ValidBidCandidate(
        String dspId,
        Bid bid,
        Instant receivedAt
) {
}
