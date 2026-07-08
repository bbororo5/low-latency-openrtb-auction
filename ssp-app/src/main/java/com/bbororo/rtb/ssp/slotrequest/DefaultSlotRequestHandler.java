package com.bbororo.rtb.ssp.slotrequest;

import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.ssp.bidrequest.BidRequestFactory;
import com.bbororo.rtb.ssp.inventory.InventoryCatalog;
import com.bbororo.rtb.ssp.inventory.InventoryPlacement;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class DefaultSlotRequestHandler implements SlotRequestHandler {

    private static final String USD = "USD";

    private final InventoryCatalog inventoryCatalog;
    private final BidRequestFactory bidRequestFactory;
    private final SlotMediaSpecValidator mediaSpecValidator;

    public DefaultSlotRequestHandler(InventoryCatalog inventoryCatalog, BidRequestFactory bidRequestFactory) {
        this(inventoryCatalog, bidRequestFactory, new SlotMediaSpecValidator());
    }

    DefaultSlotRequestHandler(
            InventoryCatalog inventoryCatalog,
            BidRequestFactory bidRequestFactory,
            SlotMediaSpecValidator mediaSpecValidator
    ) {
        this.inventoryCatalog = Objects.requireNonNull(inventoryCatalog, "inventoryCatalog must not be null");
        this.bidRequestFactory = Objects.requireNonNull(bidRequestFactory, "bidRequestFactory must not be null");
        this.mediaSpecValidator = Objects.requireNonNull(mediaSpecValidator, "mediaSpecValidator must not be null");
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

        Optional<SlotRequestValidationError> validationFailure = mediaSpecValidator.validate(request, placement);
        if (validationFailure.isPresent()) {
            SlotRequestValidationError error = validationFailure.get();
            return rejected(error.reason(), error.message());
        }

        return new AcceptedSlotRequest(bidRequestFactory.build(request, placement, receivedAt));
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

    private static RejectedSlotRequest rejected(SlotRequestRejectionReason reason, String message) {
        return new RejectedSlotRequest(reason, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
