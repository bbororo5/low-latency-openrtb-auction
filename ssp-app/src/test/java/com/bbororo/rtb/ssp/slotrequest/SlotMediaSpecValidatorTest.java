package com.bbororo.rtb.ssp.slotrequest;

import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.ssp.inventory.BannerInventorySpec;
import com.bbororo.rtb.ssp.inventory.InventoryPlacement;
import com.bbororo.rtb.ssp.inventory.VideoInventorySpec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotMediaSpecValidatorTest {

    private final SlotMediaSpecValidator validator = new SlotMediaSpecValidator();

    @Test
    void accepts_matching_banner_spec() {
        Optional<SlotRequestValidationError> result = validator.validate(
                new ProviderSlotRequest(
                        "publisher-demo",
                        "home-top-banner",
                        "banner",
                        300,
                        250,
                        null,
                        null,
                        null,
                        null,
                        120
                ),
                bannerPlacement()
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void rejects_banner_without_size() {
        Optional<SlotRequestValidationError> result = validator.validate(
                new ProviderSlotRequest(
                        "publisher-demo",
                        "home-top-banner",
                        "banner",
                        null,
                        250,
                        null,
                        null,
                        null,
                        null,
                        120
                ),
                bannerPlacement()
        );

        SlotRequestValidationError error = result.orElseThrow();
        assertEquals(SlotRequestRejectionReason.INVALID_REQUEST, error.reason());
    }

    @Test
    void rejects_video_with_unsupported_protocol() {
        Optional<SlotRequestValidationError> result = validator.validate(
                new ProviderSlotRequest(
                        "publisher-demo",
                        "pre-roll-video",
                        "video",
                        640,
                        360,
                        List.of("video/mp4"),
                        5,
                        30,
                        List.of(7),
                        120
                ),
                videoPlacement()
        );

        SlotRequestValidationError error = result.orElseThrow();
        assertEquals(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, error.reason());
    }

    @Test
    void rejects_video_with_invalid_duration_range() {
        Optional<SlotRequestValidationError> result = validator.validate(
                new ProviderSlotRequest(
                        "publisher-demo",
                        "pre-roll-video",
                        "video",
                        640,
                        360,
                        List.of("video/mp4"),
                        31,
                        30,
                        List.of(2),
                        120
                ),
                videoPlacement()
        );

        SlotRequestValidationError error = result.orElseThrow();
        assertEquals(SlotRequestRejectionReason.INVALID_REQUEST, error.reason());
    }

    private static InventoryPlacement bannerPlacement() {
        return new InventoryPlacement(
                "publisher-demo",
                "home-top-banner",
                true,
                MediaType.BANNER,
                new BannerInventorySpec(300, 250),
                new BigDecimal("0.50"),
                "USD",
                120
        );
    }

    private static InventoryPlacement videoPlacement() {
        return new InventoryPlacement(
                "publisher-demo",
                "pre-roll-video",
                true,
                MediaType.VIDEO,
                new VideoInventorySpec(640, 360, List.of("video/mp4"), 5, 30, List.of(2, 3, 5)),
                new BigDecimal("0.50"),
                "USD",
                120
        );
    }
}
