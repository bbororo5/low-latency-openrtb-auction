package com.bbororo.rtb.ssp.contract;

import com.bbororo.rtb.shared.common.AuctionType;
import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.openrtb.Banner;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.Imp;
import com.bbororo.rtb.shared.openrtb.Video;
import com.bbororo.rtb.ssp.requesthandler.AcceptedAuctionRequest;
import com.bbororo.rtb.ssp.requesthandler.DefaultRequestHandler;
import com.bbororo.rtb.ssp.requesthandler.RejectedAuctionRequest;
import com.bbororo.rtb.ssp.requesthandler.RequestHandler;
import com.bbororo.rtb.ssp.requesthandler.RequestRejectionReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SspRequestToAuctionContractTest {

    private final RequestHandler requestHandler = new DefaultRequestHandler();

    @Test
    void request_handler_passes_normalized_auction_request_to_auction_flow() {
        var receivedAt = Instant.parse("2026-06-26T10:15:30Z");
        var bidRequest = new BidRequest(
                "auction-1",
                List.of(new Imp("imp-1", new Banner(300, 250), null, null, new BigDecimal("0.75"), "USD")),
                80,
                1
        );

        var result = requestHandler.handle(bidRequest, receivedAt);

        var accepted = assertInstanceOf(AcceptedAuctionRequest.class, result);
        var auctionRequest = accepted.auctionRequest();
        assertAll(
                () -> assertEquals("auction-1", auctionRequest.requestId()),
                () -> assertEquals("imp-1", auctionRequest.impId()),
                () -> assertEquals(MediaType.BANNER, auctionRequest.mediaType()),
                () -> assertEquals(new BigDecimal("0.75"), auctionRequest.bidfloor()),
                () -> assertEquals("USD", auctionRequest.bidfloorcur()),
                () -> assertEquals(80, auctionRequest.tmax()),
                () -> assertEquals(AuctionType.FIRST_PRICE, auctionRequest.auctionType()),
                () -> assertEquals(receivedAt, auctionRequest.receivedAt())
        );
    }

    @Test
    void request_handler_rejects_invalid_bid_request_before_auction_flow() {
        var bidRequest = new BidRequest("auction-1", List.of(), 80, 1);

        var result = requestHandler.handle(bidRequest, Instant.parse("2026-06-26T10:15:30Z"));

        var rejected = assertInstanceOf(RejectedAuctionRequest.class, result);
        assertEquals(RequestRejectionReason.INVALID_REQUEST, rejected.reason());
    }

    @Test
    void request_handler_rejects_unsupported_auction_type_before_auction_flow() {
        var bidRequest = new BidRequest(
                "auction-1",
                List.of(new Imp("imp-1", new Banner(300, 250), null, null, new BigDecimal("0.75"), "USD")),
                80,
                2
        );

        var result = requestHandler.handle(bidRequest, Instant.parse("2026-06-26T10:15:30Z"));

        var rejected = assertInstanceOf(RejectedAuctionRequest.class, result);
        assertEquals(RequestRejectionReason.UNSUPPORTED_REQUEST, rejected.reason());
    }

    @Test
    void request_handler_rejects_impression_without_media_object_before_auction_flow() {
        var bidRequest = new BidRequest(
                "auction-1",
                List.of(new Imp("imp-1", null, null, null, new BigDecimal("0.75"), "USD")),
                80,
                1
        );

        var result = requestHandler.handle(bidRequest, Instant.parse("2026-06-26T10:15:30Z"));

        var rejected = assertInstanceOf(RejectedAuctionRequest.class, result);
        assertEquals(RequestRejectionReason.UNSUPPORTED_REQUEST, rejected.reason());
    }

    @Test
    void request_handler_rejects_impression_with_multiple_media_objects_before_auction_flow() {
        var bidRequest = new BidRequest(
                "auction-1",
                List.of(new Imp(
                        "imp-1",
                        new Banner(300, 250),
                        new Video(640, 360, List.of("video/mp4"), 5, 30, List.of(2)),
                        null,
                        new BigDecimal("0.75"),
                        "USD"
                )),
                80,
                1
        );

        var result = requestHandler.handle(bidRequest, Instant.parse("2026-06-26T10:15:30Z"));

        var rejected = assertInstanceOf(RejectedAuctionRequest.class, result);
        assertEquals(RequestRejectionReason.INVALID_REQUEST, rejected.reason());
    }
}
