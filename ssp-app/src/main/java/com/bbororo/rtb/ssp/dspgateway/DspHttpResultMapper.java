package com.bbororo.rtb.ssp.dspgateway;

import com.bbororo.rtb.shared.openrtb.BidResponse;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodec;
import com.bbororo.rtb.shared.openrtb.codec.OpenRtbJsonCodecException;
import com.bbororo.rtb.ssp.auctionflow.Deadline;

import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class DspHttpResultMapper {

    private static final int HTTP_OK = 200;
    private static final int HTTP_NO_CONTENT = 204;

    private final OpenRtbJsonCodec codec;

    public DspHttpResultMapper(OpenRtbJsonCodec codec) {
        this.codec = codec;
    }

    public DspCallResult fromResponse(DspHttpResponse response, Deadline deadline) {
        if (response.receivedAt().isAfter(deadline.value())) {
            return result(response.endpoint(), DspCallStatus.LATE_BID, null, response.receivedAt());
        }
        if (response.statusCode() == HTTP_NO_CONTENT) {
            return result(response.endpoint(), DspCallStatus.NO_BID, null, response.receivedAt());
        }
        if (response.statusCode() == HTTP_OK) {
            return bidReceived(response);
        }
        return result(response.endpoint(), DspCallStatus.ERROR, null, response.receivedAt());
    }

    public DspCallResult fromFailure(DspEndpoint endpoint, Throwable throwable, Instant receivedAt) {
        DspCallStatus status = isTimeout(throwable) ? DspCallStatus.TIMEOUT : DspCallStatus.ERROR;
        return result(endpoint, status, null, receivedAt);
    }

    private DspCallResult bidReceived(DspHttpResponse response) {
        try {
            BidResponse bidResponse = codec.decodeResponse(response.body());
            return result(response.endpoint(), DspCallStatus.BID_RECEIVED, bidResponse, response.receivedAt());
        } catch (OpenRtbJsonCodecException e) {
            return result(response.endpoint(), DspCallStatus.ERROR, null, response.receivedAt());
        }
    }

    private static DspCallResult result(
            DspEndpoint endpoint,
            DspCallStatus status,
            BidResponse bidResponse,
            Instant receivedAt
    ) {
        return new DspCallResult(endpoint.dspId(), status, bidResponse, receivedAt);
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return cause instanceof TimeoutException || cause instanceof HttpTimeoutException;
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
