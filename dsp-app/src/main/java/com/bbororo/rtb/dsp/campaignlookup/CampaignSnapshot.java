package com.bbororo.rtb.dsp.campaignlookup;

import com.bbororo.rtb.shared.common.MediaType;

import java.math.BigDecimal;
import java.util.List;

public record CampaignSnapshot(
        String campaignId,
        String creativeId,
        MediaType mediaType,
        BigDecimal bidCpm,
        String currency,
        List<String> advertiserDomains,
        String adm
) {
}
