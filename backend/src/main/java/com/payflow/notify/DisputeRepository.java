package com.payflow.notify;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    boolean existsByUserIdAndTransactionIdAndStatus(UUID userId, UUID transactionId, String status);

    List<Dispute> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Dispute> findByStatusOrderByCreatedAtDesc(String status);
}
