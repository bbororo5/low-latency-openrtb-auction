package com.bbororo.rtb.dsp.pricing;

import com.bbororo.rtb.dsp.matcher.MatchResult;

import java.util.Optional;

public interface Pricing {

    Optional<BidDecision> price(MatchResult matchResult);
}
