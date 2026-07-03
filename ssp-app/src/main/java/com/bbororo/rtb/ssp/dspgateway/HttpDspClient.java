package com.bbororo.rtb.ssp.dspgateway;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface HttpDspClient {

    CompletableFuture<DspHttpResponse> postBidJson(
            DspEndpoint endpoint,
            String jsonBody,
            Duration timeout
    );
}
