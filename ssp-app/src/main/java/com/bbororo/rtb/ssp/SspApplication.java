package com.bbororo.rtb.ssp;

import com.bbororo.rtb.shared.observability.PrometheusMetricsHttpHandler;
import com.bbororo.rtb.shared.observability.RtbMetrics;
import com.bbororo.rtb.shared.observability.RuntimeMetrics;
import com.bbororo.rtb.shared.openrtb.codec.JacksonOpenRtbJsonCodec;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodec;
import com.bbororo.rtb.ssp.adapter.web.JdkSspHttpServer;
import com.bbororo.rtb.ssp.adapter.web.OpenRtbAuctionHttpHandler;
import com.bbororo.rtb.ssp.auctionflow.AuctionFlow;
import com.bbororo.rtb.ssp.auctionflow.DefaultAuctionDeadlinePolicy;
import com.bbororo.rtb.ssp.auctionflow.DefaultAuctionFlow;
import com.bbororo.rtb.ssp.bidjudge.DefaultBidJudge;
import com.bbororo.rtb.ssp.dspgateway.DspEndpoint;
import com.bbororo.rtb.ssp.dspgateway.DspHttpResultMapper;
import com.bbororo.rtb.ssp.dspgateway.HttpDspGateway;
import com.bbororo.rtb.ssp.dspgateway.JdkHttpDspClient;
import com.bbororo.rtb.ssp.dspgateway.StaticDspEndpointRegistry;
import com.bbororo.rtb.ssp.requesthandler.DefaultRequestHandler;
import com.bbororo.rtb.ssp.winnerselector.FirstPriceWinnerSelector;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

public final class SspApplication {

    private static final int DEFAULT_PORT = 8080;
    private static final String DSP_ENDPOINTS_ENV = "DSP_ENDPOINTS";

    private SspApplication() {
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        JdkSspHttpServer server = createServer(port);
        server.start();
        System.out.println("SSP HTTP server started on port " + port);
    }

    public static JdkSspHttpServer createServer(int port) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        RuntimeMetrics.bindTo(registry);
        RtbMetrics metrics = new RtbMetrics(registry);
        OpenRtbJsonCodec codec = new JacksonOpenRtbJsonCodec();
        AuctionFlow auctionFlow = auctionFlow(codec, metrics);

        return new JdkSspHttpServer(
                port,
                new OpenRtbAuctionHttpHandler(codec, new DefaultRequestHandler(), auctionFlow, metrics),
                new PrometheusMetricsHttpHandler(registry)
        );
    }

    private static AuctionFlow auctionFlow(OpenRtbJsonCodec codec, RtbMetrics metrics) {
        var resultMapper = new DspHttpResultMapper(codec);
        var dspGateway = new HttpDspGateway(
                new StaticDspEndpointRegistry(configuredDspEndpoints()),
                codec,
                JdkHttpDspClient.createDefault(),
                resultMapper,
                metrics
        );

        return new DefaultAuctionFlow(
                new DefaultAuctionDeadlinePolicy(),
                dspGateway,
                new DefaultBidJudge(),
                new FirstPriceWinnerSelector()
        );
    }

    private static List<DspEndpoint> configuredDspEndpoints() {
        String configured = System.getenv(DSP_ENDPOINTS_ENV);
        if (configured == null || configured.isBlank()) {
            return defaultDspEndpoints();
        }

        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(SspApplication::endpointFromConfig)
                .toList();
    }

    private static DspEndpoint endpointFromConfig(String value) {
        String[] parts = value.split("=", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid DSP endpoint config: " + value);
        }
        return new DspEndpoint(parts[0].trim(), URI.create(parts[1].trim()));
    }

    private static List<DspEndpoint> defaultDspEndpoints() {
        return List.of(
                endpoint("dsp-a", 8081),
                endpoint("dsp-b", 8082),
                endpoint("dsp-c", 8083),
                endpoint("dsp-d", 8084)
        );
    }

    private static DspEndpoint endpoint(String dspId, int port) {
        return new DspEndpoint(dspId, URI.create("http://localhost:" + port + "/openrtb/bid"));
    }
}
