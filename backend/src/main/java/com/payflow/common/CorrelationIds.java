package com.payflow.common;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._\\-]{1,64}");

    private CorrelationIds() {
    }

    public static String resolve(String incoming) {
        if (incoming != null) {
            String trimmed = incoming.trim();
            if (SAFE.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value != null ? value : "unknown";
    }
}
