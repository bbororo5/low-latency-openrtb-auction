package com.bbororo.rtb.ssp.inventory;

import java.util.List;

public record VideoInventorySpec(
        Integer width,
        Integer height,
        List<String> mimes,
        int minDuration,
        int maxDuration,
        List<Integer> protocols
) implements InventoryMediaSpec {

    public VideoInventorySpec {
        if (width != null && width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (height != null && height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        if (mimes == null || mimes.isEmpty()) {
            throw new IllegalArgumentException("mimes must not be empty");
        }
        if (minDuration <= 0) {
            throw new IllegalArgumentException("minDuration must be positive");
        }
        if (maxDuration < minDuration) {
            throw new IllegalArgumentException("maxDuration must be greater than or equal to minDuration");
        }
        if (protocols == null || protocols.isEmpty()) {
            throw new IllegalArgumentException("protocols must not be empty");
        }
        mimes = List.copyOf(mimes);
        protocols = List.copyOf(protocols);
    }
}
