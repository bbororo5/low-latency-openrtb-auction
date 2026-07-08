package com.bbororo.rtb.ssp.inventory;

import java.util.Optional;

public interface InventoryCatalog {

    Optional<InventoryPlacement> find(String providerId, String placementId);
}
