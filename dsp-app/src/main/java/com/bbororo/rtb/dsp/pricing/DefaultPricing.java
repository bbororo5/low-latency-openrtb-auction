package com.bbororo.rtb.dsp.pricing;

import com.bbororo.rtb.dsp.campaignlookup.BidContext;
import com.bbororo.rtb.dsp.campaignlookup.CampaignSnapshot;
import com.bbororo.rtb.dsp.campaignlookup.Creative;
import com.bbororo.rtb.dsp.matcher.MatchGrade;
import com.bbororo.rtb.dsp.matcher.MatchResult;
import com.bbororo.rtb.dsp.matcher.MatchedCampaign;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

public final class DefaultPricing implements Pricing {

    private static final String USD = "USD";
    private static final BigDecimal HIGH_MULTIPLIER = new BigDecimal("1.20");
    private static final BigDecimal MEDIUM_MULTIPLIER = BigDecimal.ONE;
    private static final BigDecimal LOW_MULTIPLIER = new BigDecimal("0.80");

    @Override
    public PricingResult decide(MatchResult matchResult) {
        if (matchResult.matchedCampaigns().isEmpty()) {
            return new NoBidPrice(PricingNoBidReason.NO_MATCHED_CAMPAIGN);
        }

        BidContext bidContext = matchResult.bidContext();
        return matchResult.matchedCampaigns().stream()
                .map(matchedCampaign -> priceCandidate(bidContext, matchedCampaign))
                .filter(Objects::nonNull)
                .max(Comparator
                        .comparing((BidDecision decision) -> decision.price())
                        .thenComparing(BidDecision::campaignId))
                .<PricingResult>map(PricedBid::new)
                .orElseGet(() -> new NoBidPrice(PricingNoBidReason.BID_BELOW_FLOOR));
    }

    private static BidDecision priceCandidate(BidContext bidContext, MatchedCampaign matchedCampaign) {
        CampaignSnapshot campaign = matchedCampaign.campaign();
        if (!USD.equals(bidContext.currency()) || !USD.equals(campaign.bidPolicy().currency())) {
            return null;
        }
        if (hasMissingCreative(campaign.creative())) {
            return null;
        }

        BigDecimal price = campaign.bidPolicy()
                .baseBidCpm()
                .multiply(multiplier(matchedCampaign.grade()))
                .min(campaign.bidPolicy().maxBidCpm());

        if (price.compareTo(bidContext.bidfloor()) < 0) {
            return null;
        }

        return new BidDecision(
                bidContext.requestId(),
                campaign.seat(),
                bidContext.impId(),
                price,
                campaign.bidPolicy().currency(),
                campaign.campaignId(),
                campaign.advertiserId(),
                campaign.creative().creativeId(),
                campaign.creative().advertiserDomains(),
                campaign.mediaType(),
                campaign.creative().adm(),
                matchedCampaign.grade()
        );
    }

    private static boolean hasMissingCreative(Creative creative) {
        return creative == null
                || isBlank(creative.creativeId())
                || creative.advertiserDomains() == null
                || creative.advertiserDomains().isEmpty()
                || isBlank(creative.adm());
    }

    private static BigDecimal multiplier(MatchGrade grade) {
        return switch (grade) {
            case HIGH -> HIGH_MULTIPLIER;
            case MEDIUM -> MEDIUM_MULTIPLIER;
            case LOW -> LOW_MULTIPLIER;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
