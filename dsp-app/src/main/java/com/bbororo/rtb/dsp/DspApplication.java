package com.bbororo.rtb.dsp;

import com.bbororo.rtb.dsp.adapter.web.JdkDspHttpServer;
import com.bbororo.rtb.dsp.adapter.web.DelayedHttpHandler;
import com.bbororo.rtb.dsp.adapter.web.OpenRtbBidHttpHandler;
import com.bbororo.rtb.dsp.bidbuilder.OpenRtbBidBuilder;
import com.bbororo.rtb.dsp.bidhandler.BidHandler;
import com.bbororo.rtb.dsp.bidhandler.DefaultBidHandler;
import com.bbororo.rtb.dsp.campaignlookup.BannerTarget;
import com.bbororo.rtb.dsp.campaignlookup.BidPolicy;
import com.bbororo.rtb.dsp.campaignlookup.CampaignLookup;
import com.bbororo.rtb.dsp.campaignlookup.CampaignSnapshot;
import com.bbororo.rtb.dsp.campaignlookup.Creative;
import com.bbororo.rtb.dsp.campaignlookup.InMemoryCampaignLookup;
import com.bbororo.rtb.dsp.campaignlookup.NativeTarget;
import com.bbororo.rtb.dsp.campaignlookup.TargetingRule;
import com.bbororo.rtb.dsp.campaignlookup.VideoTarget;
import com.bbororo.rtb.dsp.matcher.DefaultMatcher;
import com.bbororo.rtb.dsp.pricing.DefaultPricing;
import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.observability.PrometheusMetricsHttpHandler;
import com.bbororo.rtb.shared.openrtb.codec.JacksonOpenRtbJsonCodec;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

public final class DspApplication {

    private static final int DEFAULT_PORT = 8081;
    private static final String DEFAULT_DSP_ID = "dsp-a";
    private static final DspMode DEFAULT_MODE = DspMode.NORMAL_MEDIUM;

    private DspApplication() {
    }

    public static void main(String[] args) {
        DspRuntimeConfig config = DspRuntimeConfig.from(args);
        JdkDspHttpServer server = createServer(config);
        server.start();
        System.out.println("DSP HTTP server started: dspId=" + config.dspId()
                + ", mode=" + config.mode()
                + ", port=" + config.port());
    }

    public static JdkDspHttpServer createServer(int port) {
        return createServer(new DspRuntimeConfig(DEFAULT_DSP_ID, port, DEFAULT_MODE));
    }

    public static JdkDspHttpServer createServer(DspRuntimeConfig config) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        JacksonOpenRtbJsonCodec codec = new JacksonOpenRtbJsonCodec();
        BidHandler bidHandler = bidHandler(config);
        OpenRtbBidHttpHandler bidHttpHandler = new OpenRtbBidHttpHandler(codec, bidHandler);

        return new JdkDspHttpServer(
                config.port(),
                withModeDelay(bidHttpHandler, config.mode()),
                new PrometheusMetricsHttpHandler(registry)
        );
    }

    private static BidHandler bidHandler(DspRuntimeConfig config) {
        CampaignLookup campaignLookup = new InMemoryCampaignLookup(sampleCampaigns(config));
        return new DefaultBidHandler(
                campaignLookup,
                new DefaultMatcher(),
                new DefaultPricing(),
                new OpenRtbBidBuilder()
        );
    }

    private static com.sun.net.httpserver.HttpHandler withModeDelay(
            OpenRtbBidHttpHandler handler,
            DspMode mode
    ) {
        if (mode == DspMode.TIMEOUT) {
            return new DelayedHttpHandler(handler, Duration.ofMillis(500));
        }
        return handler;
    }

    private static List<CampaignSnapshot> sampleCampaigns(DspRuntimeConfig config) {
        return switch (config.mode()) {
            case NORMAL_MEDIUM -> mediumBidCampaigns(config.dspId(), "1.20", "1.80");
            case NORMAL_HIGH -> mediumBidCampaigns(config.dspId(), "2.00", "2.80");
            case NO_BID, TIMEOUT -> List.of();
        };
    }

    private static List<CampaignSnapshot> mediumBidCampaigns(
            String dspId,
            String bannerBaseBid,
            String bannerMaxBid
    ) {
        return List.of(
                new CampaignSnapshot(
                        dspId + "-camp-banner-001",
                        dspId + "-adv-001",
                        dspId,
                        true,
                        MediaType.BANNER,
                        new TargetingRule(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                new BannerTarget(300, 250),
                                null,
                                null
                        ),
                        new BidPolicy(new BigDecimal(bannerBaseBid), new BigDecimal(bannerMaxBid), "USD"),
                        new Creative(dspId + "-cr-banner-001", List.of("advertiser.example"), "<div>banner</div>")
                ),
                new CampaignSnapshot(
                        dspId + "-camp-video-001",
                        dspId + "-adv-002",
                        dspId,
                        true,
                        MediaType.VIDEO,
                        new TargetingRule(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                null,
                                new VideoTarget(List.of("video/mp4"), 5, 30, List.of(2, 3, 5)),
                                null
                        ),
                        new BidPolicy(new BigDecimal("2.00"), new BigDecimal("3.00"), "USD"),
                        new Creative(dspId + "-cr-video-001", List.of("video-advertiser.example"), "<VAST version=\"3.0\"></VAST>")
                ),
                new CampaignSnapshot(
                        dspId + "-camp-native-001",
                        dspId + "-adv-003",
                        dspId,
                        true,
                        MediaType.NATIVE,
                        new TargetingRule(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                null,
                                null,
                                new NativeTarget(true)
                        ),
                        new BidPolicy(new BigDecimal("1.50"), new BigDecimal("2.40"), "USD"),
                        new Creative(dspId + "-cr-native-001", List.of("native-advertiser.example"), "{\"native\":{\"assets\":[]}}")
                )
        );
    }

    public enum DspMode {
        NORMAL_MEDIUM,
        NORMAL_HIGH,
        NO_BID,
        TIMEOUT;

        private static DspMode from(String value) {
            return DspMode.valueOf(value.trim().replace('-', '_').toUpperCase());
        }
    }

    public record DspRuntimeConfig(
            String dspId,
            int port,
            DspMode mode
    ) {
        private static DspRuntimeConfig from(String[] args) {
            String dspId = args.length > 0 ? args[0] : DEFAULT_DSP_ID;
            int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
            DspMode mode = args.length > 2 ? DspMode.from(args[2]) : DEFAULT_MODE;
            return new DspRuntimeConfig(dspId, port, mode);
        }
    }
}
