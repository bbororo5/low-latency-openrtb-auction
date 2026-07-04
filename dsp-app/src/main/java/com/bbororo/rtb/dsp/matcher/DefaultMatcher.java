package com.bbororo.rtb.dsp.matcher;

import com.bbororo.rtb.dsp.campaignlookup.BannerSpec;
import com.bbororo.rtb.dsp.campaignlookup.BidContext;
import com.bbororo.rtb.dsp.campaignlookup.CampaignCandidates;
import com.bbororo.rtb.dsp.campaignlookup.CampaignSnapshot;
import com.bbororo.rtb.dsp.campaignlookup.MediaSpec;
import com.bbororo.rtb.dsp.campaignlookup.NativeSpec;
import com.bbororo.rtb.dsp.campaignlookup.TargetingRule;
import com.bbororo.rtb.dsp.campaignlookup.VideoSpec;

import java.util.ArrayList;
import java.util.List;

public final class DefaultMatcher implements Matcher {

    @Override
    public MatchResult match(BidContext bidContext, CampaignCandidates candidates) {
        List<MatchedCampaign> matchedCampaigns = new ArrayList<>();
        int rejectedCount = 0;

        for (CampaignSnapshot campaign : candidates.campaigns()) {
            if (matches(bidContext, campaign)) {
                matchedCampaigns.add(new MatchedCampaign(campaign, grade(bidContext, campaign.targeting())));
            } else {
                rejectedCount++;
            }
        }

        return new MatchResult(bidContext, List.copyOf(matchedCampaigns), rejectedCount);
    }

    private static boolean matches(BidContext bidContext, CampaignSnapshot campaign) {
        TargetingRule targeting = campaign.targeting();
        return campaign.mediaType() == bidContext.mediaType()
                && matchesMediaSpec(bidContext.mediaSpec(), targeting)
                && containsOrWildcard(targeting.countries(), bidContext.device().country())
                && containsOrWildcard(targeting.deviceTypes(), bidContext.device().deviceType())
                && containsOrWildcard(targeting.siteDomains(), bidContext.site().domain())
                && overlapsOrWildcard(targeting.siteCategories(), bidContext.site().categories());
    }

    private static boolean matchesMediaSpec(MediaSpec mediaSpec, TargetingRule targeting) {
        return switch (mediaSpec) {
            case BannerSpec banner -> targeting.banner() != null
                    && equalsOrWildcard(targeting.banner().width(), banner.width())
                    && equalsOrWildcard(targeting.banner().height(), banner.height());
            case VideoSpec video -> targeting.video() != null
                    && overlapsOrWildcard(targeting.video().mimes(), video.mimes())
                    && durationContains(targeting.video().minDuration(), targeting.video().maxDuration(), video.minDuration(), video.maxDuration())
                    && overlapsOrWildcard(targeting.video().protocols(), video.protocols());
            case NativeSpec ignored -> targeting.nativeAd() != null && targeting.nativeAd().supported();
        };
    }

    private static MatchGrade grade(BidContext bidContext, TargetingRule targeting) {
        int score = 0;
        score += matchedSpecific(targeting.countries(), bidContext.device().country());
        score += matchedSpecific(targeting.deviceTypes(), bidContext.device().deviceType());
        score += matchedSpecific(targeting.siteDomains(), bidContext.site().domain());
        score += matchedSpecific(targeting.siteCategories(), bidContext.site().categories());

        if (score >= 3) {
            return MatchGrade.HIGH;
        }
        if (score >= 1) {
            return MatchGrade.MEDIUM;
        }
        return MatchGrade.LOW;
    }

    private static int matchedSpecific(List<String> expected, String actual) {
        return expected != null && !expected.isEmpty() && expected.contains(actual) ? 1 : 0;
    }

    private static int matchedSpecific(List<String> expected, List<String> actual) {
        return expected != null && !expected.isEmpty() && actual != null && expected.stream().anyMatch(actual::contains) ? 1 : 0;
    }

    private static boolean containsOrWildcard(List<String> expected, String actual) {
        return expected == null || expected.isEmpty() || expected.contains(actual);
    }

    private static <T> boolean overlapsOrWildcard(List<T> expected, List<T> actual) {
        return expected == null || expected.isEmpty() || (actual != null && expected.stream().anyMatch(actual::contains));
    }

    private static boolean equalsOrWildcard(Integer expected, Integer actual) {
        return expected == null || expected.equals(actual);
    }

    private static boolean durationContains(Integer targetMin, Integer targetMax, Integer requestMin, Integer requestMax) {
        boolean minOk = targetMin == null || requestMin == null || targetMin <= requestMin;
        boolean maxOk = targetMax == null || requestMax == null || targetMax >= requestMax;
        return minOk && maxOk;
    }
}
