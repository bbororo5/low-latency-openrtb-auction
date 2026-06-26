package com.bbororo.rtb.shared.openrtb;

import java.util.List;

public record BidRequest(
        String id,
        List<Imp> imp,
        Integer tmax,
        Integer at
) {
}
