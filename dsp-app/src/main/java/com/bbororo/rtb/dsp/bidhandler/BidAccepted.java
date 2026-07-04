package com.bbororo.rtb.dsp.bidhandler;

import com.bbororo.rtb.shared.openrtb.BidResponse;

public record BidAccepted(BidResponse bidResponse) implements BidHandlingResult {
}
