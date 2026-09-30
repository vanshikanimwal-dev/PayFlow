package com.payflow.notify;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<AppNotification, UUID> {

    List<AppNotification> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);
}
