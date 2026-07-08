package com.bbororo.rtb.ssp.bidrequest;

import com.bbororo.rtb.shared.common.AuctionType;
import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.openrtb.Banner;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.Imp;
import com.bbororo.rtb.shared.openrtb.Video;
import com.bbororo.rtb.ssp.auctionflow.AuctionCommand;
import com.bbororo.rtb.ssp.auctionflow.AuctionRequest;
import com.bbororo.rtb.ssp.inventory.BannerInventorySpec;
import com.bbororo.rtb.ssp.inventory.InventoryPlacement;
import com.bbororo.rtb.ssp.inventory.VideoInventorySpec;
import com.bbororo.rtb.ssp.slotrequest.ProviderSlotRequest;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class OpenRtbBidRequestFactory implements BidRequestFactory {

    private static final int OPENRTB_FIRST_PRICE = 1;

    private final RequestIdGenerator requestIdGenerator;

    public OpenRtbBidRequestFactory(RequestIdGenerator requestIdGenerator) {
        this.requestIdGenerator = Objects.requireNonNull(requestIdGenerator, "requestIdGenerator must not be null");
    }

    @Override
    public AuctionCommand build(ProviderSlotRequest slotRequest, InventoryPlacement placement, Instant receivedAt) {
        Objects.requireNonNull(slotRequest, "slotRequest must not be null");
        Objects.requireNonNull(placement, "placement must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");

        String requestId = requestIdGenerator.nextId("req");
        String impId = requestIdGenerator.nextId("imp");
        Integer tmax = slotRequest.tmax() == null ? placement.defaultTmax() : slotRequest.tmax();
        Imp impression = impression(impId, slotRequest, placement);
        BidRequest bidRequest = new BidRequest(requestId, List.of(impression), tmax, OPENRTB_FIRST_PRICE);
        AuctionRequest auctionRequest = new AuctionRequest(
                requestId,
                impId,
                placement.mediaType(),
                placement.bidfloor(),
                placement.currency(),
                tmax,
                AuctionType.FIRST_PRICE,
                receivedAt
        );
        return new AuctionCommand(bidRequest, auctionRequest);
    }

    private static Imp impression(String impId, ProviderSlotRequest slotRequest, InventoryPlacement placement) {
        return switch (placement.mediaType()) {
            case BANNER -> {
                BannerInventorySpec spec = (BannerInventorySpec) placement.mediaSpec();
                yield new Imp(
                        impId,
                        new Banner(spec.width(), spec.height()),
                        null,
                        null,
                        placement.bidfloor(),
                        placement.currency()
                );
            }
            case VIDEO -> {
                VideoInventorySpec spec = (VideoInventorySpec) placement.mediaSpec();
                yield new Imp(
                        impId,
                        null,
                        new Video(
                                slotRequest.width(),
                                slotRequest.height(),
                                slotRequest.mimes(),
                                slotRequest.minDuration(),
                                slotRequest.maxDuration(),
                                slotRequest.protocols()
                        ),
                        null,
                        placement.bidfloor(),
                        placement.currency()
                );
            }
            case NATIVE -> throw new IllegalArgumentException("provider-facing native request is not supported");
        };
    }
}
