package com.bbororo.rtb.dsp.campaignlookup;

import java.util.List;

public record TargetingRule(
        List<String> countries,
        List<String> deviceTypes,
        List<String> siteDomains,
        List<String> siteCategories,
        BannerTarget banner,
        VideoTarget video,
        NativeTarget nativeAd
) {
}
