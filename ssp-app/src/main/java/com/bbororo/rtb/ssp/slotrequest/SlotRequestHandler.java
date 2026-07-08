package com.bbororo.rtb.ssp.slotrequest;

import java.time.Instant;

public interface SlotRequestHandler {

    SlotRequestHandlingResult handle(ProviderSlotRequest request, Instant receivedAt);
}
