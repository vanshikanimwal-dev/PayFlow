package com.payflow.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, Long> {

    List<ReconciliationItem> findByRunIdOrderByIdAsc(UUID runId);
}
