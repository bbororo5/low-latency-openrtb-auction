package com.bbororo.rtb.dsp.campaignlookup;

import java.util.List;

public record VideoTarget(
        List<String> mimes,
        Integer minDuration,
        Integer maxDuration,
        List<Integer> protocols
) {
}
