package com.bbororo.rtb.ssp.slotrequest;

import com.bbororo.rtb.ssp.auctionflow.AuctionCommand;

public record AcceptedSlotRequest(
        AuctionCommand auctionCommand
) implements SlotRequestHandlingResult {
}
