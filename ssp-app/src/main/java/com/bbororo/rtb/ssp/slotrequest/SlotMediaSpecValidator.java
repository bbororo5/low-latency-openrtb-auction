package com.bbororo.rtb.ssp.slotrequest;

import com.bbororo.rtb.ssp.inventory.BannerInventorySpec;
import com.bbororo.rtb.ssp.inventory.InventoryPlacement;
import com.bbororo.rtb.ssp.inventory.VideoInventorySpec;

import java.util.List;
import java.util.Optional;

final class SlotMediaSpecValidator {

    Optional<SlotRequestValidationError> validate(ProviderSlotRequest request, InventoryPlacement placement) {
        return switch (placement.mediaType()) {
            case BANNER -> validateBanner(request, (BannerInventorySpec) placement.mediaSpec());
            case VIDEO -> validateVideo(request, (VideoInventorySpec) placement.mediaSpec());
            case NATIVE -> failure(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Native slot requests are out of scope.");
        };
    }

    private static Optional<SlotRequestValidationError> validateBanner(
            ProviderSlotRequest request,
            BannerInventorySpec spec
    ) {
        if (request.width() == null || request.height() == null) {
            return failure(SlotRequestRejectionReason.INVALID_REQUEST, "Banner width and height are required.");
        }
        if (request.width() <= 0 || request.height() <= 0) {
            return failure(SlotRequestRejectionReason.INVALID_REQUEST, "Banner width and height must be positive.");
        }
        if (request.width() != spec.width() || request.height() != spec.height()) {
            return failure(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Banner size is not supported by placement.");
        }
        return Optional.empty();
    }

    private static Optional<SlotRequestValidationError> validateVideo(
            ProviderSlotRequest request,
            VideoInventorySpec spec
    ) {
        if (request.mimes() == null || request.mimes().isEmpty()
                || request.minDuration() == null
                || request.maxDuration() == null
                || request.protocols() == null
                || request.protocols().isEmpty()) {
            return failure(SlotRequestRejectionReason.INVALID_REQUEST, "Video mimes, duration, and protocols are required.");
        }
        if (request.minDuration() <= 0 || request.maxDuration() < request.minDuration()) {
            return failure(SlotRequestRejectionReason.INVALID_REQUEST, "Video duration range is invalid.");
        }
        if (request.width() != null && request.width() <= 0 || request.height() != null && request.height() <= 0) {
            return failure(SlotRequestRejectionReason.INVALID_REQUEST, "Video width and height must be positive when provided.");
        }
        if (!overlaps(spec.mimes(), request.mimes())) {
            return failure(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Video MIME type is not supported by placement.");
        }
        if (request.minDuration() < spec.minDuration() || request.maxDuration() > spec.maxDuration()) {
            return failure(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Video duration is not supported by placement.");
        }
        if (!overlaps(spec.protocols(), request.protocols())) {
            return failure(SlotRequestRejectionReason.UNSUPPORTED_REQUEST, "Video protocol is not supported by placement.");
        }
        return Optional.empty();
    }

    private static <T> boolean overlaps(List<T> left, List<T> right) {
        return left != null && right != null && left.stream().anyMatch(right::contains);
    }

    private static Optional<SlotRequestValidationError> failure(
            SlotRequestRejectionReason reason,
            String message
    ) {
        return Optional.of(new SlotRequestValidationError(reason, message));
    }
}
