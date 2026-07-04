package com.bbororo.rtb.dsp.campaignlookup;

import com.bbororo.rtb.shared.common.MediaType;

public record CampaignSnapshot(
        String campaignId,
        String advertiserId,
        String seat,
        boolean enabled,
        MediaType mediaType,
        TargetingRule targeting,
        BidPolicy bidPolicy,
        Creative creative
) {
}
