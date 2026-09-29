package com.payflow.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

    List<ReconciliationRun> findAllByOrderByStartedAtDesc();
}
