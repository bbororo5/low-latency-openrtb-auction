package com.bbororo.rtb.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("E2E smoke scenarios require minimal SSP/DSP implementations.")
class AuctionSmokeE2eTest {

    @Test
    void returns_winner_when_at_least_one_dsp_returns_valid_bid() {
    }

    @Test
    void returns_no_winner_when_all_dsps_return_no_bid() {
    }

    @Test
    void returns_winner_from_valid_bid_even_when_some_dsps_timeout() {
    }
}
