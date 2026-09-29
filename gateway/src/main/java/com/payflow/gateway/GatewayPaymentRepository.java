package com.payflow.gateway;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayPaymentRepository extends JpaRepository<GatewayPaymentEntity, String> {

    Optional<GatewayPaymentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<GatewayPaymentEntity> findByReference(String reference);

    List<GatewayPaymentEntity> findByStatus(String status);
}
