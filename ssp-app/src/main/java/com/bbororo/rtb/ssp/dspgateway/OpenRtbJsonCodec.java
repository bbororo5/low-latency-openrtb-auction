package com.bbororo.rtb.ssp.dspgateway;

import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.BidResponse;

public interface OpenRtbJsonCodec {

    String encodeRequest(BidRequest bidRequest);

    BidResponse decodeResponse(String body);
}
