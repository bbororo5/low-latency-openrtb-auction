package com.bbororo.rtb.ssp.slotrequest;

public record RejectedSlotRequest(
        SlotRequestRejectionReason reason,
        String message
) implements SlotRequestHandlingResult {
}
