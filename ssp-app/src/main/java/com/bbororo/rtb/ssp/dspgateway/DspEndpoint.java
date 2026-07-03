package com.bbororo.rtb.ssp.dspgateway;

import java.net.URI;
import java.util.Objects;

public record DspEndpoint(
        String dspId,
        URI bidUri
) {

    public DspEndpoint {
        if (dspId == null || dspId.isBlank()) {
            throw new IllegalArgumentException("dspId must not be blank");
        }
        Objects.requireNonNull(bidUri, "bidUri must not be null");
    }
}
