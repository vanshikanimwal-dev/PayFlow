package com.payflow.gatewayclient;

/** Gateway refused the call before doing work (4xx, or the circuit was open so nothing was sent). */
public class GatewayRejectedException extends RuntimeException {

    public GatewayRejectedException(String message) {
        super(message);
    }
}
