package com.payflow.payment;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentRequest p where p.id = :id")
    Optional<PaymentRequest> lockById(@Param("id") UUID id);

    List<PaymentRequest> findByStatusAndExpiresAtBefore(PaymentRequestStatus status, Instant cutoff);
}
