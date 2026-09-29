package com.payflow.gatewayclient;

/** Timeout or 5xx. The gateway may have accepted the payment. Do not assume failure. */
public class GatewayUnknownException extends RuntimeException {

    public GatewayUnknownException(String message, Throwable cause) {
        super(message, cause);
    }

    public GatewayUnknownException(String message) {
        super(message);
    }
}
