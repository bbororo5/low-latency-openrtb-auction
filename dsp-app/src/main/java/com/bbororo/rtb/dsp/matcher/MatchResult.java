package com.bbororo.rtb.dsp.matcher;

import com.bbororo.rtb.dsp.campaignlookup.BidContext;

import java.util.List;

public record MatchResult(
        BidContext bidContext,
        List<MatchedCampaign> matchedCampaigns
) {
}
