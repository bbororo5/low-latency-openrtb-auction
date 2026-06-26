package com.bbororo.rtb.shared.contract;

import com.bbororo.rtb.shared.common.MediaType;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.Imp;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SspToDspBidRequestContractTest {

    @Test
    void bid_request_contains_fields_required_by_dsp_bid_decision() {
        var impression = new Imp("imp-1", MediaType.BANNER, new BigDecimal("0.75"), "USD");
        var bidRequest = new BidRequest("auction-1", List.of(impression), 80, 1);

        assertAll(
                () -> assertEquals("auction-1", bidRequest.id()),
                () -> assertEquals("imp-1", bidRequest.imp().getFirst().id()),
                () -> assertEquals(MediaType.BANNER, bidRequest.imp().getFirst().mediaType()),
                () -> assertEquals(new BigDecimal("0.75"), bidRequest.imp().getFirst().bidfloor()),
                () -> assertEquals("USD", bidRequest.imp().getFirst().bidfloorcur()),
                () -> assertEquals(80, bidRequest.tmax()),
                () -> assertEquals(1, bidRequest.at())
        );
    }
}
