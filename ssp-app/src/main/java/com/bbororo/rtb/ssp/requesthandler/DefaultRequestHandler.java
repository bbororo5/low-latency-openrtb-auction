package com.bbororo.rtb.ssp.requesthandler;

import com.bbororo.rtb.shared.common.AuctionType;
import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.Imp;
import com.bbororo.rtb.ssp.auctionflow.AuctionRequest;

import java.math.BigDecimal;
import java.time.Instant;

public final class DefaultRequestHandler implements RequestHandler {

    private static final int OPENRTB_FIRST_PRICE = 1;
    private static final String DEFAULT_CURRENCY = "USD";

    @Override
    public RequestHandlingResult handle(BidRequest bidRequest, Instant receivedAt) {
        if (bidRequest == null || isBlank(bidRequest.id()) || bidRequest.imp() == null || bidRequest.imp().isEmpty()) {
            return reject(RequestRejectionReason.INVALID_REQUEST, "BidRequest must include id and at least one impression.");
        }
        if (bidRequest.imp().size() != 1) {
            return reject(RequestRejectionReason.UNSUPPORTED_REQUEST, "Only single-impression BidRequest is supported.");
        }
        if (bidRequest.at() != null && bidRequest.at() != OPENRTB_FIRST_PRICE) {
            return reject(RequestRejectionReason.UNSUPPORTED_REQUEST, "Only first-price auctions are supported.");
        }

        Imp impression = bidRequest.imp().getFirst();
        if (isBlank(impression.id())) {
            return reject(RequestRejectionReason.INVALID_REQUEST, "Impression id is required.");
        }
        MediaType mediaType = resolveMediaType(impression);
        if (mediaType == null) {
            return reject(RequestRejectionReason.UNSUPPORTED_REQUEST, "One supported impression media object is required.");
        }
        if (hasMultipleMediaObjects(impression)) {
            return reject(RequestRejectionReason.INVALID_REQUEST, "Only one impression media object is allowed.");
        }
        if (!DEFAULT_CURRENCY.equals(currencyOrDefault(impression))) {
            return reject(RequestRejectionReason.UNSUPPORTED_REQUEST, "Only USD currency is supported.");
        }
        RequestHandlingResult mediaValidation = validateMediaSpec(impression, mediaType);
        if (mediaValidation instanceof RejectedAuctionRequest) {
            return mediaValidation;
        }

        var auctionRequest = new AuctionRequest(
                bidRequest.id(),
                impression.id(),
                mediaType,
                bidfloorOrZero(impression),
                currencyOrDefault(impression),
                bidRequest.tmax(),
                AuctionType.FIRST_PRICE,
                receivedAt
        );
        return new AcceptedAuctionRequest(auctionRequest);
    }

    private static RejectedAuctionRequest reject(RequestRejectionReason reason, String message) {
        return new RejectedAuctionRequest(reason, message);
    }

    private static MediaType resolveMediaType(Imp impression) {
        if (impression.banner() != null) {
            return MediaType.BANNER;
        }
        if (impression.video() != null) {
            return MediaType.VIDEO;
        }
        if (impression.nativeAd() != null) {
            return MediaType.NATIVE;
        }
        return null;
    }

    private static boolean hasMultipleMediaObjects(Imp impression) {
        int count = 0;
        if (impression.banner() != null) {
            count++;
        }
        if (impression.video() != null) {
            count++;
        }
        if (impression.nativeAd() != null) {
            count++;
        }
        return count > 1;
    }

    private static RequestHandlingResult validateMediaSpec(Imp impression, MediaType mediaType) {
        return switch (mediaType) {
            case BANNER -> {
                if (impression.banner().w() == null || impression.banner().h() == null
                        || impression.banner().w() <= 0 || impression.banner().h() <= 0) {
                    yield reject(RequestRejectionReason.INVALID_REQUEST, "Banner width and height are required.");
                }
                yield null;
            }
            case VIDEO -> {
                if (impression.video().mimes() == null || impression.video().mimes().isEmpty()
                        || impression.video().minduration() == null
                        || impression.video().maxduration() == null
                        || impression.video().protocols() == null
                        || impression.video().protocols().isEmpty()) {
                    yield reject(RequestRejectionReason.INVALID_REQUEST, "Video mimes, duration, and protocols are required.");
                }
                if (impression.video().minduration() <= 0
                        || impression.video().maxduration() < impression.video().minduration()) {
                    yield reject(RequestRejectionReason.INVALID_REQUEST, "Video duration range is invalid.");
                }
                yield null;
            }
            case NATIVE -> {
                if (impression.nativeAd().request() == null || impression.nativeAd().request().isBlank()) {
                    yield reject(RequestRejectionReason.INVALID_REQUEST, "Native request is required.");
                }
                yield null;
            }
        };
    }

    private static BigDecimal bidfloorOrZero(Imp impression) {
        if (impression.bidfloor() == null) {
            return BigDecimal.ZERO;
        }
        return impression.bidfloor();
    }

    private static String currencyOrDefault(Imp impression) {
        if (isBlank(impression.bidfloorcur())) {
            return DEFAULT_CURRENCY;
        }
        return impression.bidfloorcur();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
