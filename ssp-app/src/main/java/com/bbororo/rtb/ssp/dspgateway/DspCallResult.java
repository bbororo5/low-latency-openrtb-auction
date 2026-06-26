package com.bbororo.rtb.ssp.dspgateway;

import com.bbororo.rtb.shared.openrtb.BidResponse;

import java.time.Instant;

public record DspCallResult(
        String dspId,
        DspCallStatus status,
        BidResponse bidResponse,
        Instant receivedAt
) {
}
