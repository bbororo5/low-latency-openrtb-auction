package com.bbororo.rtb.ssp.inventory;

import com.bbororo.rtb.shared.common.MediaType;

import java.math.BigDecimal;
import java.util.Objects;

public record InventoryPlacement(
        String providerId,
        String placementId,
        boolean enabled,
        MediaType mediaType,
        InventoryMediaSpec mediaSpec,
        BigDecimal bidfloor,
        String currency,
        Integer defaultTmax
) {

    public InventoryPlacement {
        if (isBlank(providerId)) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (isBlank(placementId)) {
            throw new IllegalArgumentException("placementId must not be blank");
        }
        Objects.requireNonNull(mediaType, "mediaType must not be null");
        Objects.requireNonNull(mediaSpec, "mediaSpec must not be null");
        Objects.requireNonNull(bidfloor, "bidfloor must not be null");
        if (bidfloor.signum() < 0) {
            throw new IllegalArgumentException("bidfloor must not be negative");
        }
        if (isBlank(currency)) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (defaultTmax != null && defaultTmax <= 0) {
            throw new IllegalArgumentException("defaultTmax must be positive");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
