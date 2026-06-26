package com.bbororo.rtb.shared.openrtb;

import java.math.BigDecimal;
import java.util.List;

public record Bid(
        String id,
        String impid,
        BigDecimal price,
        String cid,
        String crid,
        List<String> adomain,
        Integer mtype,
        String adm
) {
}
