package com.bbororo.rtb.shared.openrtb;

import com.bbororo.rtb.shared.common.MediaType;

import java.math.BigDecimal;

public record Imp(
        String id,
        MediaType mediaType,
        BigDecimal bidfloor,
        String bidfloorcur
) {
}
