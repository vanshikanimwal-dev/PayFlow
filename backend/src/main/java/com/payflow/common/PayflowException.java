package com.payflow.common;

import org.springframework.http.HttpStatus;

public class PayflowException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;

    public PayflowException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
