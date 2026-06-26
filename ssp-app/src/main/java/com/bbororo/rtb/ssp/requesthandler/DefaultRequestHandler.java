package com.bbororo.rtb.ssp.requesthandler;

import com.bbororo.rtb.shared.common.AuctionType;
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
        if (bidRequest.at() != null && bidRequest.at() != OPENRTB_FIRST_PRICE) {
            return reject(RequestRejectionReason.UNSUPPORTED_REQUEST, "Only first-price auctions are supported.");
        }

        Imp impression = bidRequest.imp().getFirst();
        if (isBlank(impression.id())) {
            return reject(RequestRejectionReason.INVALID_REQUEST, "Impression id is required.");
        }
        if (impression.mediaType() == null) {
            return reject(RequestRejectionReason.UNSUPPORTED_REQUEST, "Impression media type is required.");
        }

        var auctionRequest = new AuctionRequest(
                bidRequest.id(),
                impression.id(),
                impression.mediaType(),
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
