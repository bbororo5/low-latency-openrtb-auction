package com.bbororo.rtb.ssp.slotrequest;

import java.util.List;

public record ProviderSlotRequest(
        String providerId,
        String placementId,
        String mediaType,
        Integer width,
        Integer height,
        List<String> mimes,
        Integer minDuration,
        Integer maxDuration,
        List<Integer> protocols,
        Integer tmax
) {

    public ProviderSlotRequest {
        if (mimes != null) {
            mimes = List.copyOf(mimes);
        }
        if (protocols != null) {
            protocols = List.copyOf(protocols);
        }
    }
}
