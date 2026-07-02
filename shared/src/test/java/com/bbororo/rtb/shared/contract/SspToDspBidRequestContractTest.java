package com.bbororo.rtb.shared.contract;

import com.bbororo.rtb.shared.openrtb.Banner;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.Imp;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SspToDspBidRequestContractTest {

    @Test
    void bid_request_contains_fields_required_by_dsp_bid_decision() {
        var impression = new Imp("imp-1", new Banner(300, 250), null, null, new BigDecimal("0.75"), "USD");
        var bidRequest = new BidRequest("auction-1", List.of(impression), 80, 1);

        assertAll(
                () -> assertEquals("auction-1", bidRequest.id()),
                () -> assertEquals("imp-1", bidRequest.imp().getFirst().id()),
                () -> assertNotNull(bidRequest.imp().getFirst().banner()),
                () -> assertNull(bidRequest.imp().getFirst().video()),
                () -> assertNull(bidRequest.imp().getFirst().nativeAd()),
                () -> assertEquals(300, bidRequest.imp().getFirst().banner().w()),
                () -> assertEquals(250, bidRequest.imp().getFirst().banner().h()),
                () -> assertEquals(new BigDecimal("0.75"), bidRequest.imp().getFirst().bidfloor()),
                () -> assertEquals("USD", bidRequest.imp().getFirst().bidfloorcur()),
                () -> assertEquals(80, bidRequest.tmax()),
                () -> assertEquals(1, bidRequest.at())
        );
    }
}
