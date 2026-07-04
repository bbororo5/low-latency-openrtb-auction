package com.bbororo.rtb.dsp.bidbuilder;

import com.bbororo.rtb.dsp.pricing.BidDecision;
import com.bbororo.rtb.dsp.pricing.PricedBid;
import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.openrtb.Bid;
import com.bbororo.rtb.shared.openrtb.BidResponse;
import com.bbororo.rtb.shared.openrtb.SeatBid;

import java.util.List;

public final class OpenRtbBidBuilder implements BidBuilder {

    private static final String USD = "USD";

    @Override
    public BidResponse build(PricedBid pricedBid) {
        BidDecision decision = pricedBid.decision();
        Bid bid = new Bid(
                bidId(decision),
                decision.impId(),
                decision.price(),
                decision.campaignId(),
                decision.creativeId(),
                decision.advertiserDomains(),
                openRtbMediaType(decision.mediaType()),
                decision.adm()
        );

        SeatBid seatBid = new SeatBid(decision.seat(), List.of(bid));
        return new BidResponse(decision.requestId(), List.of(seatBid), USD);
    }

    private static String bidId(BidDecision decision) {
        return decision.requestId() + ":" + decision.campaignId() + ":" + decision.creativeId();
    }

    private static int openRtbMediaType(MediaType mediaType) {
        return switch (mediaType) {
            case BANNER -> 1;
            case VIDEO -> 2;
            case NATIVE -> 4;
        };
    }
}
