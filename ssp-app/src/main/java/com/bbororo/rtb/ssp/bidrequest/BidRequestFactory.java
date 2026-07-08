package com.bbororo.rtb.ssp.bidrequest;

import com.bbororo.rtb.ssp.auctionflow.AuctionCommand;
import com.bbororo.rtb.ssp.inventory.InventoryPlacement;
import com.bbororo.rtb.ssp.slotrequest.ProviderSlotRequest;

import java.time.Instant;

public interface BidRequestFactory {

    AuctionCommand build(ProviderSlotRequest slotRequest, InventoryPlacement placement, Instant receivedAt);
}
