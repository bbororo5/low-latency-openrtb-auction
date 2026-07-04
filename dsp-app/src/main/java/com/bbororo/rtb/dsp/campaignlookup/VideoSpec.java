package com.bbororo.rtb.dsp.campaignlookup;

import java.util.List;

public record VideoSpec(
        Integer width,
        Integer height,
        List<String> mimes,
        Integer minDuration,
        Integer maxDuration,
        List<Integer> protocols
) implements MediaSpec {
}
