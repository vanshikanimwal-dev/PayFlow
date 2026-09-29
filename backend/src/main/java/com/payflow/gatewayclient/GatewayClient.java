package com.payflow.gatewayclient;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface GatewayClient {

    GatewayPayment createPayment(UUID reference, long amountMinor, String method, String callbackUrl);

    Optional<GatewayPayment> findByReference(UUID reference);

    String settlementCsv(LocalDate date);
}
