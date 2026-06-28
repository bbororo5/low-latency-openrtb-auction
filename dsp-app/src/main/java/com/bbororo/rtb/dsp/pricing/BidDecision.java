package com.bbororo.rtb.dsp.pricing;

import com.bbororo.rtb.shared.common.MediaType;

import java.math.BigDecimal;
import java.util.List;

public record BidDecision(
        String requestId,
        String seat,
        String impId,
        BigDecimal price,
        String currency,
        String campaignId,
        String creativeId,
        List<String> advertiserDomains,
        MediaType mediaType,
        String adm
) {
}
