package com.bbororo.rtb.ssp.bidrequest;

import java.util.UUID;

public final class UuidRequestIdGenerator implements RequestIdGenerator {

    @Override
    public String nextId(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "id" : prefix;
        return normalizedPrefix + "-" + UUID.randomUUID();
    }
}
