package com.bbororo.rtb.ssp.dspgateway;

import java.time.Instant;

public record DspHttpResponse(
        DspEndpoint endpoint,
        int statusCode,
        String body,
        Instant receivedAt
) {
}
