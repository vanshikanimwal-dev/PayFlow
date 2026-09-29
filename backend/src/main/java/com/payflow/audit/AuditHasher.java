package com.payflow.audit;

import com.payflow.common.Hashes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class AuditHasher {

    private AuditHasher() {
    }

    public static String hash(
            String prevHash,
            UUID actorId,
            String actorRole,
            String action,
            String entity,
            String entityId,
            String before,
            String after,
            Instant createdAt) {
        String payload = String.join(
                "|",
                prevHash == null ? "" : prevHash,
                actorId == null ? "" : actorId.toString(),
                actorRole == null ? "" : actorRole,
                action == null ? "" : action,
                entity == null ? "" : entity,
                entityId == null ? "" : entityId,
                before == null ? "" : before,
                after == null ? "" : after,
                Long.toString(createdAt.truncatedTo(ChronoUnit.MILLIS).toEpochMilli()));
        return Hashes.sha256(payload);
    }
}
