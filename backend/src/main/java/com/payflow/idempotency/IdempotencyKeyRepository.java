package com.payflow.idempotency;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {

    @Modifying
    @Query("delete from IdempotencyKey k where k.expiresAt < :now")
    int deleteExpired(Instant now);
}
