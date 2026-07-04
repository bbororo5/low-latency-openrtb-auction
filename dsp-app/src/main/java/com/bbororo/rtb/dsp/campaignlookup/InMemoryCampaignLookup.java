package com.bbororo.rtb.dsp.campaignlookup;

import java.util.List;

public final class InMemoryCampaignLookup implements CampaignLookup {

    private final List<CampaignSnapshot> campaigns;

    public InMemoryCampaignLookup(List<CampaignSnapshot> campaigns) {
        this.campaigns = List.copyOf(campaigns);
    }

    @Override
    public CampaignCandidates findCandidates(BidContext bidContext) {
        List<CampaignSnapshot> candidates = campaigns.stream()
                .filter(CampaignSnapshot::enabled)
                .filter(campaign -> campaign.mediaType() == bidContext.mediaType())
                .toList();

        return new CampaignCandidates(bidContext, candidates);
    }
}
