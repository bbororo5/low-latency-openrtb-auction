package com.bbororo.rtb.shared.observability;

import java.util.Locale;

public final class RtbMetricTags {

    public static final String UNKNOWN = "unknown";

    private RtbMetricTags() {
    }

    public static String value(Enum<?> value) {
        if (value == null) {
            return UNKNOWN;
        }
        return value.name().toLowerCase(Locale.ROOT);
    }

    public static String value(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
