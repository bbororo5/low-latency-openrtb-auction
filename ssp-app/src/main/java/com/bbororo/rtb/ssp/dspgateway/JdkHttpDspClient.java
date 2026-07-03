package com.bbororo.rtb.ssp.dspgateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class JdkHttpDspClient implements HttpDspClient {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String ACCEPT = "Accept";
    private static final String APPLICATION_JSON = "application/json";

    private final HttpClient httpClient;

    public JdkHttpDspClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    public static JdkHttpDspClient createDefault() {
        return new JdkHttpDspClient(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(100))
                .build());
    }

    @Override
    public CompletableFuture<DspHttpResponse> postBidJson(
            DspEndpoint endpoint,
            String jsonBody,
            Duration timeout
    ) {
        HttpRequest request = HttpRequest.newBuilder(endpoint.bidUri())
                .timeout(normalize(timeout))
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .header(ACCEPT, APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> toDspHttpResponse(endpoint, response));
    }

    private static DspHttpResponse toDspHttpResponse(
            DspEndpoint endpoint,
            HttpResponse<String> response
    ) {
        return new DspHttpResponse(
                endpoint,
                response.statusCode(),
                response.body(),
                Instant.now()
        );
    }

    private static Duration normalize(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return Duration.ofMillis(1);
        }
        return timeout;
    }
}
