package com.bbororo.rtb.dsp.pricing;

import com.bbororo.rtb.dsp.matcher.MatchResult;

public interface Pricing {

    PricingResult decide(MatchResult matchResult);
}
