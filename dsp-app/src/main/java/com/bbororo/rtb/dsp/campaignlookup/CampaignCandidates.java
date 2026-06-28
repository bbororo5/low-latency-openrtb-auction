package com.bbororo.rtb.dsp.campaignlookup;

import java.util.List;

public record CampaignCandidates(
        BidContext bidContext,
        List<CampaignSnapshot> campaigns
) {
}
