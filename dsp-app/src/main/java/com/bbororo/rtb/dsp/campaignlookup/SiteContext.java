package com.bbororo.rtb.dsp.campaignlookup;

import java.util.List;

public record SiteContext(
        String domain,
        List<String> categories
) {
}
