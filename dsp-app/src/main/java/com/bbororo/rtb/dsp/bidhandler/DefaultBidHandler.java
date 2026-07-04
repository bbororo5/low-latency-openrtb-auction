package com.bbororo.rtb.dsp.bidhandler;

import com.bbororo.rtb.dsp.campaignlookup.BannerSpec;
import com.bbororo.rtb.dsp.campaignlookup.BidContext;
import com.bbororo.rtb.dsp.campaignlookup.DeviceContext;
import com.bbororo.rtb.dsp.campaignlookup.MediaSpec;
import com.bbororo.rtb.dsp.campaignlookup.NativeSpec;
import com.bbororo.rtb.dsp.campaignlookup.SiteContext;
import com.bbororo.rtb.dsp.campaignlookup.VideoSpec;
import com.bbororo.rtb.shared.common.AuctionType;
import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.openrtb.Banner;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.Imp;
import com.bbororo.rtb.shared.openrtb.NativeAd;
import com.bbororo.rtb.shared.openrtb.Video;

import java.math.BigDecimal;
import java.util.List;

public final class DefaultBidHandler implements BidHandler {

    private static final String USD = "USD";

    @Override
    public BidHandlingResult handle(BidRequest bidRequest) {
        if (bidRequest == null || isBlank(bidRequest.id())) {
            return new NoBid(NoBidReason.INVALID_REQUEST);
        }
        if (bidRequest.imp() == null || bidRequest.imp().size() != 1) {
            return new NoBid(NoBidReason.UNSUPPORTED_REQUEST);
        }
        if (bidRequest.at() != null && bidRequest.at() != 1) {
            return new NoBid(NoBidReason.UNSUPPORTED_REQUEST);
        }

        Imp imp = bidRequest.imp().getFirst();
        if (imp == null || isBlank(imp.id())) {
            return new NoBid(NoBidReason.INVALID_REQUEST);
        }

        String currency = imp.bidfloorcur() == null ? USD : imp.bidfloorcur();
        if (!USD.equals(currency)) {
            return new NoBid(NoBidReason.UNSUPPORTED_REQUEST);
        }

        MediaResolution mediaResolution = resolveMedia(imp);
        if (mediaResolution.reason() != null) {
            return new NoBid(mediaResolution.reason());
        }

        BidContext bidContext = new BidContext(
                bidRequest.id(),
                imp.id(),
                mediaResolution.mediaType(),
                imp.bidfloor() == null ? BigDecimal.ZERO : imp.bidfloor(),
                currency,
                AuctionType.FIRST_PRICE,
                bidRequest.tmax(),
                new SiteContext(null, List.of()),
                new DeviceContext(null, null, null),
                mediaResolution.mediaSpec()
        );

        return new AcceptedBidRequest(bidContext);
    }

    private static MediaResolution resolveMedia(Imp imp) {
        int mediaCount = mediaCount(imp);
        if (mediaCount == 0) {
            return MediaResolution.rejected(NoBidReason.UNSUPPORTED_REQUEST);
        }
        if (mediaCount > 1) {
            return MediaResolution.rejected(NoBidReason.INVALID_REQUEST);
        }
        if (imp.banner() != null) {
            return resolveBanner(imp.banner());
        }
        if (imp.video() != null) {
            return resolveVideo(imp.video());
        }
        return resolveNative(imp.nativeAd());
    }

    private static MediaResolution resolveBanner(Banner banner) {
        if (banner.w() == null || banner.h() == null) {
            return MediaResolution.rejected(NoBidReason.INVALID_REQUEST);
        }
        return MediaResolution.accepted(MediaType.BANNER, new BannerSpec(banner.w(), banner.h()));
    }

    private static MediaResolution resolveVideo(Video video) {
        if (video.mimes() == null || video.mimes().isEmpty()
                || video.minduration() == null
                || video.maxduration() == null
                || video.protocols() == null
                || video.protocols().isEmpty()) {
            return MediaResolution.rejected(NoBidReason.INVALID_REQUEST);
        }
        MediaSpec spec = new VideoSpec(
                video.w(),
                video.h(),
                video.mimes(),
                video.minduration(),
                video.maxduration(),
                video.protocols()
        );
        return MediaResolution.accepted(MediaType.VIDEO, spec);
    }

    private static MediaResolution resolveNative(NativeAd nativeAd) {
        if (nativeAd == null || isBlank(nativeAd.request())) {
            return MediaResolution.rejected(NoBidReason.INVALID_REQUEST);
        }
        return MediaResolution.accepted(MediaType.NATIVE, new NativeSpec(nativeAd.request()));
    }

    private static int mediaCount(Imp imp) {
        int count = 0;
        count += imp.banner() == null ? 0 : 1;
        count += imp.video() == null ? 0 : 1;
        count += imp.nativeAd() == null ? 0 : 1;
        return count;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record MediaResolution(
            MediaType mediaType,
            MediaSpec mediaSpec,
            NoBidReason reason
    ) {
        private static MediaResolution accepted(MediaType mediaType, MediaSpec mediaSpec) {
            return new MediaResolution(mediaType, mediaSpec, null);
        }

        private static MediaResolution rejected(NoBidReason reason) {
            return new MediaResolution(null, null, reason);
        }
    }
}
