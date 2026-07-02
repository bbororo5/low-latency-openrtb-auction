package com.bbororo.rtb.shared.openrtb;

import java.math.BigDecimal;

public record Imp(
        String id,
        Banner banner,
        Video video,
        NativeAd nativeAd,
        BigDecimal bidfloor,
        String bidfloorcur
) {
}
