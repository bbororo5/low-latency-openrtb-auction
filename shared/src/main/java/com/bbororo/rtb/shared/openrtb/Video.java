package com.bbororo.rtb.shared.openrtb;

import java.util.List;

public record Video(
        Integer w,
        Integer h,
        List<String> mimes,
        Integer minduration,
        Integer maxduration,
        List<Integer> protocols
) {
}
