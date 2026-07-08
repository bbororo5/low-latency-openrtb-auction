package com.bbororo.rtb.ssp.inventory;

public record BannerInventorySpec(
        int width,
        int height
) implements InventoryMediaSpec {

    public BannerInventorySpec {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
    }
}
