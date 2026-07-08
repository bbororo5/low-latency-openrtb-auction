package com.bbororo.rtb.ssp.slotrequest;

import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.ssp.bidrequest.OpenRtbBidRequestFactory;
import com.bbororo.rtb.ssp.inventory.BannerInventorySpec;
import com.bbororo.rtb.ssp.inventory.InMemoryInventoryCatalog;
import com.bbororo.rtb.ssp.inventory.InventoryPlacement;
import com.bbororo.rtb.ssp.inventory.VideoInventorySpec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DefaultSlotRequestHandlerTest {

    @Test
    void accepts_matching_banner_slot_request() {
        DefaultSlotRequestHandler handler = handler();
        ProviderSlotRequest request = bannerRequest("publisher-demo", "home-top-banner", 300, 250);

        SlotRequestHandlingResult result = handler.handle(request, Instant.EPOCH);

        AcceptedSlotRequest accepted = assertInstanceOf(AcceptedSlotRequest.class, result);
        assertEquals(MediaType.BANNER, accepted.auctionCommand().auctionRequest().mediaType());
        assertEquals(new BigDecimal("0.50"), accepted.auctionCommand().auctionRequest().bidfloor());
    }

    @Test
    void rejects_unknown_placement() {
        DefaultSlotRequestHandler handler = handler();

        SlotRequestHandlingResult result = handler.handle(
                bannerRequest("publisher-demo", "unknown", 300, 250),
                Instant.EPOCH
        );

        RejectedSlotRequest rejected = assertInstanceOf(RejectedSlotRequest.class, result);
        assertEquals(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, rejected.reason());
    }

    @Test
    void rejects_banner_size_mismatch() {
        DefaultSlotRequestHandler handler = handler();

        SlotRequestHandlingResult result = handler.handle(
                bannerRequest("publisher-demo", "home-top-banner", 728, 90),
                Instant.EPOCH
        );

        RejectedSlotRequest rejected = assertInstanceOf(RejectedSlotRequest.class, result);
        assertEquals(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, rejected.reason());
    }

    @Test
    void rejects_native_slot_requests_on_provider_facing_path() {
        DefaultSlotRequestHandler handler = handler();
        ProviderSlotRequest request = new ProviderSlotRequest(
                "publisher-demo",
                "home-top-banner",
                "native",
                null,
                null,
                null,
                null,
                null,
                null,
                120
        );

        SlotRequestHandlingResult result = handler.handle(request, Instant.EPOCH);

        RejectedSlotRequest rejected = assertInstanceOf(RejectedSlotRequest.class, result);
        assertEquals(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, rejected.reason());
    }

    @Test
    void rejects_video_without_required_constraints() {
        DefaultSlotRequestHandler handler = handler();
        ProviderSlotRequest request = new ProviderSlotRequest(
                "publisher-demo",
                "pre-roll-video",
                "video",
                640,
                360,
                List.of(),
                5,
                30,
                List.of(2),
                120
        );

        SlotRequestHandlingResult result = handler.handle(request, Instant.EPOCH);

        RejectedSlotRequest rejected = assertInstanceOf(RejectedSlotRequest.class, result);
        assertEquals(SlotRequestRejectionReason.INVALID_REQUEST, rejected.reason());
    }

    @Test
    void rejects_media_spec_validation_failure_before_bid_request_creation() {
        DefaultSlotRequestHandler handler = handler();

        SlotRequestHandlingResult result = handler.handle(
                bannerRequest("publisher-demo", "home-top-banner", 300, null),
                Instant.EPOCH
        );

        RejectedSlotRequest rejected = assertInstanceOf(RejectedSlotRequest.class, result);
        assertEquals(SlotRequestRejectionReason.INVALID_REQUEST, rejected.reason());
    }

    private static DefaultSlotRequestHandler handler() {
        var catalog = new InMemoryInventoryCatalog(List.of(
                new InventoryPlacement(
                        "publisher-demo",
                        "home-top-banner",
                        true,
                        MediaType.BANNER,
                        new BannerInventorySpec(300, 250),
                        new BigDecimal("0.50"),
                        "USD",
                        120
                ),
                new InventoryPlacement(
                        "publisher-demo",
                        "pre-roll-video",
                        true,
                        MediaType.VIDEO,
                        new VideoInventorySpec(640, 360, List.of("video/mp4"), 5, 30, List.of(2, 3, 5)),
                        new BigDecimal("0.50"),
                        "USD",
                        120
                )
        ));
        var factory = new OpenRtbBidRequestFactory(prefix -> prefix + "-fixed");
        return new DefaultSlotRequestHandler(catalog, factory);
    }

    private static ProviderSlotRequest bannerRequest(String providerId, String placementId, Integer width, Integer height) {
        return new ProviderSlotRequest(
                providerId,
                placementId,
                "banner",
                width,
                height,
                null,
                null,
                null,
                null,
                120
        );
    }
}
