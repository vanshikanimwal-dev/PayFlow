package com.payflow.gatewayclient;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface GatewayClient {

    GatewayPayment createPayment(UUID reference, long amountMinor, String method, String callbackUrl);

    Optional<GatewayPayment> findByReference(UUID reference);

    /** Card refund. Must be called with no database transaction open. The key is the refund transaction id. */
    GatewayPayment refund(String paymentId, long amountMinor, String idempotencyKey);

    String settlementCsv(LocalDate date);
}
