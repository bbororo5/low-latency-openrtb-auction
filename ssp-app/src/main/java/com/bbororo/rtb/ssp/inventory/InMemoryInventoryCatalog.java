package com.bbororo.rtb.ssp.inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryInventoryCatalog implements InventoryCatalog {

    private final Map<Key, InventoryPlacement> placements;

    public InMemoryInventoryCatalog(List<InventoryPlacement> placements) {
        Objects.requireNonNull(placements, "placements must not be null");
        Map<Key, InventoryPlacement> values = new HashMap<>();
        for (InventoryPlacement placement : placements) {
            values.put(new Key(placement.providerId(), placement.placementId()), placement);
        }
        this.placements = Map.copyOf(values);
    }

    @Override
    public Optional<InventoryPlacement> find(String providerId, String placementId) {
        return Optional.ofNullable(placements.get(new Key(providerId, placementId)));
    }

    private record Key(String providerId, String placementId) {
    }
}
