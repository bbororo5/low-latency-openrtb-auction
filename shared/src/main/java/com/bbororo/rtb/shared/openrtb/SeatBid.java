package com.bbororo.rtb.shared.openrtb;

import java.util.List;

public record SeatBid(
        String seat,
        List<Bid> bid
) {
}
