package com.payflow.request;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoneyRequestRepository extends JpaRepository<MoneyRequest, UUID> {

    List<MoneyRequest> findByRequesterIdOrPayerEmailOrderByCreatedAtDesc(UUID requesterId, String payerEmail);
}
