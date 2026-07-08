package com.bbororo.rtb.ssp.slotrequest;

record SlotRequestValidationError(
        SlotRequestRejectionReason reason,
        String message
) {
}
