package com.payflow.common;

import org.springframework.http.HttpStatus;

public class RateLimitedException extends PayflowException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
