package com.bbororo.rtb.shared.openrtb.codec;

import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.BidResponse;

public interface OpenRtbJsonCodec {

    String encodeRequest(BidRequest bidRequest);

    BidRequest decodeRequest(String body);

    String encodeResponse(BidResponse bidResponse);

    BidResponse decodeResponse(String body);
}
