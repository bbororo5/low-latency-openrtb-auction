package com.bbororo.rtb.dsp.matcher;

import com.bbororo.rtb.dsp.campaignlookup.BidContext;
import com.bbororo.rtb.dsp.campaignlookup.CampaignCandidates;

public interface Matcher {

    MatchResult match(BidContext bidContext, CampaignCandidates candidates);
}
