package com.payflow.common;

import java.time.Instant;

/**
 * Optional success envelope. SPEC section 7 returns most payloads directly;
 * use this only when an endpoint needs a wrapper.
 */
public record ApiResponse<T>(T data, String correlationId, Instant timestamp) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, CorrelationIds.current(), Instant.now());
    }
}
