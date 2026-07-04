package com.bbororo.rtb.dsp.campaignlookup;

import java.util.List;

public record Creative(
        String creativeId,
        List<String> advertiserDomains,
        String adm
) {
}
