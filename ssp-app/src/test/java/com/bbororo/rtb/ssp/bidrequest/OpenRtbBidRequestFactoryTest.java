package com.bbororo.rtb.ssp.bidrequest;

import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.openrtb.Imp;
import com.bbororo.rtb.ssp.auctionflow.AuctionCommand;
import com.bbororo.rtb.ssp.inventory.BannerInventorySpec;
import com.bbororo.rtb.ssp.inventory.InventoryPlacement;
import com.bbororo.rtb.ssp.inventory.VideoInventorySpec;
import com.bbororo.rtb.ssp.slotrequest.ProviderSlotRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenRtbBidRequestFactoryTest {

    @Test
    void builds_banner_bid_request_from_slot_request_and_inventory() {
        var factory = new OpenRtbBidRequestFactory(prefix -> prefix + "-fixed");
        InventoryPlacement placement = new InventoryPlacement(
                "publisher-demo",
                "home-top-banner",
                true,
                MediaType.BANNER,
                new BannerInventorySpec(300, 250),
                new BigDecimal("0.50"),
                "USD",
                120
        );
        ProviderSlotRequest slotRequest = new ProviderSlotRequest(
                "publisher-demo",
                "home-top-banner",
                "banner",
                300,
                250,
                null,
                null,
                null,
                null,
                null
        );

        AuctionCommand command = factory.build(slotRequest, placement, Instant.EPOCH);

        assertEquals("req-fixed", command.bidRequest().id());
        assertEquals(1, command.bidRequest().imp().size());
        assertEquals("imp-fixed", command.bidRequest().imp().getFirst().id());
        assertEquals(300, command.bidRequest().imp().getFirst().banner().w());
        assertEquals(250, command.bidRequest().imp().getFirst().banner().h());
        assertNull(command.bidRequest().imp().getFirst().video());
        assertEquals(new BigDecimal("0.50"), command.bidRequest().imp().getFirst().bidfloor());
        assertEquals("USD", command.bidRequest().imp().getFirst().bidfloorcur());
        assertEquals(120, command.bidRequest().tmax());
        assertEquals(MediaType.BANNER, command.auctionRequest().mediaType());
        assertAuctionCommandInvariants(command, MediaType.BANNER);
    }

    @Test
    void builds_video_bid_request_from_slot_request_constraints() {
        var factory = new OpenRtbBidRequestFactory(prefix -> prefix + "-fixed");
        InventoryPlacement placement = new InventoryPlacement(
                "publisher-demo",
                "pre-roll-video",
                true,
                MediaType.VIDEO,
                new VideoInventorySpec(640, 360, List.of("video/mp4"), 5, 30, List.of(2, 3, 5)),
                new BigDecimal("0.50"),
                "USD",
                120
        );
        ProviderSlotRequest slotRequest = new ProviderSlotRequest(
                "publisher-demo",
                "pre-roll-video",
                "video",
                640,
                360,
                List.of("video/mp4"),
                10,
                20,
                List.of(2),
                90
        );

        AuctionCommand command = factory.build(slotRequest, placement, Instant.EPOCH);

        assertNull(command.bidRequest().imp().getFirst().banner());
        assertEquals(List.of("video/mp4"), command.bidRequest().imp().getFirst().video().mimes());
        assertEquals(10, command.bidRequest().imp().getFirst().video().minduration());
        assertEquals(20, command.bidRequest().imp().getFirst().video().maxduration());
        assertEquals(List.of(2), command.bidRequest().imp().getFirst().video().protocols());
        assertEquals(90, command.bidRequest().tmax());
        assertEquals(MediaType.VIDEO, command.auctionRequest().mediaType());
        assertAuctionCommandInvariants(command, MediaType.VIDEO);
    }

    private static void assertAuctionCommandInvariants(AuctionCommand command, MediaType expectedMediaType) {
        assertEquals(command.bidRequest().id(), command.auctionRequest().requestId());
        assertEquals(1, command.bidRequest().imp().size());

        Imp impression = command.bidRequest().imp().getFirst();
        assertEquals(impression.id(), command.auctionRequest().impId());
        assertEquals(impression.bidfloor(), command.auctionRequest().bidfloor());
        assertEquals(impression.bidfloorcur(), command.auctionRequest().bidfloorcur());
        assertEquals(expectedMediaType, command.auctionRequest().mediaType());

        switch (expectedMediaType) {
            case BANNER -> {
                assertNotNull(impression.banner());
                assertNull(impression.video());
                assertNull(impression.nativeAd());
            }
            case VIDEO -> {
                assertNull(impression.banner());
                assertNotNull(impression.video());
                assertNull(impression.nativeAd());
            }
            case NATIVE -> throw new AssertionError("Provider-facing native request is out of scope.");
        }
    }
}
