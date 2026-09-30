package com.payflow.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, UUID> {

    List<ScheduledTransfer> findByUserIdOrderByNextRunOnAsc(UUID userId);

    List<ScheduledTransfer> findByActiveTrueAndNextRunOnLessThanEqual(LocalDate day);
}
