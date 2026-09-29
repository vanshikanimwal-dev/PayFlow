package com.payflow.gatewayclient;

public record GatewayPayment(String paymentId, String status, long amountMinor, String payUrl, String reference) {
}
