package com.bbororo.rtb.dsp;

import com.bbororo.rtb.dsp.adapter.web.JdkDspHttpServer;
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
import java.util.List;

public final class DspApplication {

    private static final int DEFAULT_PORT = 8081;

    private DspApplication() {
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        JdkDspHttpServer server = createServer(port);
        server.start();
        System.out.println("DSP HTTP server started on port " + port);
    }

    public static JdkDspHttpServer createServer(int port) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        JacksonOpenRtbJsonCodec codec = new JacksonOpenRtbJsonCodec();
        BidHandler bidHandler = bidHandler();

        return new JdkDspHttpServer(
                port,
                new OpenRtbBidHttpHandler(codec, bidHandler),
                new PrometheusMetricsHttpHandler(registry)
        );
    }

    private static BidHandler bidHandler() {
        CampaignLookup campaignLookup = new InMemoryCampaignLookup(sampleCampaigns());
        return new DefaultBidHandler(
                campaignLookup,
                new DefaultMatcher(),
                new DefaultPricing(),
                new OpenRtbBidBuilder()
        );
    }

    private static List<CampaignSnapshot> sampleCampaigns() {
        return List.of(
                new CampaignSnapshot(
                        "camp-banner-001",
                        "adv-001",
                        "seat-a",
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
                        new BidPolicy(new BigDecimal("1.20"), new BigDecimal("1.80"), "USD"),
                        new Creative("cr-banner-001", List.of("advertiser.example"), "<div>banner</div>")
                ),
                new CampaignSnapshot(
                        "camp-video-001",
                        "adv-002",
                        "seat-a",
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
                        new Creative("cr-video-001", List.of("video-advertiser.example"), "<VAST version=\"3.0\"></VAST>")
                ),
                new CampaignSnapshot(
                        "camp-native-001",
                        "adv-003",
                        "seat-a",
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
                        new Creative("cr-native-001", List.of("native-advertiser.example"), "{\"native\":{\"assets\":[]}}")
                )
        );
    }
}
