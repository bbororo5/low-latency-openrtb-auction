package com.bbororo.rtb.shared.openrtb;

import java.math.BigDecimal;

public record Imp(
        String id,
        BigDecimal bidfloor,
        String bidfloorcur
) {
}
