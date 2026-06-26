package com.bbororo.rtb.ssp.requesthandler;

public record RejectedAuctionRequest(
        RequestRejectionReason reason,
        String message
) implements RequestHandlingResult {
}
