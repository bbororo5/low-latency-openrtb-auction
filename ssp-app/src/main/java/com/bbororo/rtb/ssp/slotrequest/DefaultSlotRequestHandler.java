package com.bbororo.rtb.ssp.slotrequest;

import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.ssp.bidrequest.BidRequestFactory;
import com.bbororo.rtb.ssp.inventory.BannerInventorySpec;
import com.bbororo.rtb.ssp.inventory.InventoryCatalog;
import com.bbororo.rtb.ssp.inventory.InventoryPlacement;
import com.bbororo.rtb.ssp.inventory.VideoInventorySpec;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class DefaultSlotRequestHandler implements SlotRequestHandler {

    private static final String USD = "USD";

    private final InventoryCatalog inventoryCatalog;
    private final BidRequestFactory bidRequestFactory;

    public DefaultSlotRequestHandler(InventoryCatalog inventoryCatalog, BidRequestFactory bidRequestFactory) {
        this.inventoryCatalog = Objects.requireNonNull(inventoryCatalog, "inventoryCatalog must not be null");
        this.bidRequestFactory = Objects.requireNonNull(bidRequestFactory, "bidRequestFactory must not be null");
    }

    @Override
    public SlotRequestHandlingResult handle(ProviderSlotRequest request, Instant receivedAt) {
        if (request == null || isBlank(request.providerId()) || isBlank(request.placementId())) {
            return rejected(SlotRequestRejectionReason.INVALID_REQUEST, "providerId and placementId are required.");
        }
        if (request.tmax() != null && request.tmax() <= 0) {
            return rejected(SlotRequestRejectionReason.INVALID_REQUEST, "tmax must be positive.");
        }

        MediaType requestedMediaType = mediaType(request.mediaType());
        if (requestedMediaType == null) {
            return rejected(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Only banner and video slot requests are supported.");
        }

        return inventoryCatalog.find(request.providerId(), request.placementId())
                .<SlotRequestHandlingResult>map(placement -> handleWithPlacement(request, requestedMediaType, placement, receivedAt))
                .orElseGet(() -> rejected(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Unknown placement."));
    }

    private SlotRequestHandlingResult handleWithPlacement(
            ProviderSlotRequest request,
            MediaType requestedMediaType,
            InventoryPlacement placement,
            Instant receivedAt
    ) {
        if (!placement.enabled()) {
            return rejected(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Placement is disabled.");
        }
        if (!USD.equals(placement.currency())) {
            return rejected(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Only USD inventory is supported.");
        }
        if (placement.mediaType() != requestedMediaType) {
            return rejected(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Slot media type does not match placement.");
        }

        Optional<RejectedSlotRequest> validationFailure = validateMediaSpec(request, placement);
        if (validationFailure.isPresent()) {
            return validationFailure.get();
        }

        return new AcceptedSlotRequest(bidRequestFactory.build(request, placement, receivedAt));
    }

    private static Optional<RejectedSlotRequest> validateMediaSpec(ProviderSlotRequest request, InventoryPlacement placement) {
        return switch (placement.mediaType()) {
            case BANNER -> validateBanner(request, (BannerInventorySpec) placement.mediaSpec());
            case VIDEO -> validateVideo(request, (VideoInventorySpec) placement.mediaSpec());
            case NATIVE -> validationFailure(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Native slot requests are out of scope.");
        };
    }

    private static Optional<RejectedSlotRequest> validateBanner(ProviderSlotRequest request, BannerInventorySpec spec) {
        if (request.width() == null || request.height() == null) {
            return validationFailure(SlotRequestRejectionReason.INVALID_REQUEST, "Banner width and height are required.");
        }
        if (request.width() <= 0 || request.height() <= 0) {
            return validationFailure(SlotRequestRejectionReason.INVALID_REQUEST, "Banner width and height must be positive.");
        }
        if (request.width() != spec.width() || request.height() != spec.height()) {
            return validationFailure(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Banner size is not supported by placement.");
        }
        return Optional.empty();
    }

    private static Optional<RejectedSlotRequest> validateVideo(ProviderSlotRequest request, VideoInventorySpec spec) {
        if (request.mimes() == null || request.mimes().isEmpty()
                || request.minDuration() == null
                || request.maxDuration() == null
                || request.protocols() == null
                || request.protocols().isEmpty()) {
            return validationFailure(SlotRequestRejectionReason.INVALID_REQUEST, "Video mimes, duration, and protocols are required.");
        }
        if (request.minDuration() <= 0 || request.maxDuration() < request.minDuration()) {
            return validationFailure(SlotRequestRejectionReason.INVALID_REQUEST, "Video duration range is invalid.");
        }
        if (request.width() != null && request.width() <= 0 || request.height() != null && request.height() <= 0) {
            return validationFailure(SlotRequestRejectionReason.INVALID_REQUEST, "Video width and height must be positive when provided.");
        }
        if (!overlaps(spec.mimes(), request.mimes())) {
            return validationFailure(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Video MIME type is not supported by placement.");
        }
        if (request.minDuration() < spec.minDuration() || request.maxDuration() > spec.maxDuration()) {
            return validationFailure(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Video duration is not supported by placement.");
        }
        if (!overlaps(spec.protocols(), request.protocols())) {
            return validationFailure(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Video protocol is not supported by placement.");
        }
        return Optional.empty();
    }

    private static MediaType mediaType(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BANNER" -> MediaType.BANNER;
            case "VIDEO" -> MediaType.VIDEO;
            default -> null;
        };
    }

    private static <T> boolean overlaps(List<T> left, List<T> right) {
        return left != null && right != null && left.stream().anyMatch(right::contains);
    }

    private static Optional<RejectedSlotRequest> validationFailure(SlotRequestRejectionReason reason, String message) {
        return Optional.of(new RejectedSlotRequest(reason, message));
    }

    private static RejectedSlotRequest rejected(SlotRequestRejectionReason reason, String message) {
        return new RejectedSlotRequest(reason, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
