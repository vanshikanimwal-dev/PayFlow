package com.payflow.audit;

import com.payflow.common.CorrelationIds;
import com.payflow.common.Jsons;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository logs;
    private final AuditChainHeadRepository heads;
    private final Jsons jsons;
    private final Clock clock;

    public AuditService(AuditLogRepository logs, AuditChainHeadRepository heads, Jsons jsons, Clock clock) {
        this.logs = logs;
        this.heads = heads;
        this.jsons = jsons;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID actorId, String actorRole, String action, String entity, String entityId, Object before, Object after) {
        AuditChainHead head = heads.lockHead();
        Instant createdAt = clock.instant();
        String beforeJson = jsons.write(before);
        String afterJson = jsons.write(after);
        String prev = head.getHash();
        String hash = AuditHasher.hash(prev, actorId, actorRole, action, entity, entityId, beforeJson, afterJson, createdAt);
        AuditLog row = new AuditLog();
        row.setActorId(actorId);
        row.setActorRole(actorRole);
        row.setAction(action);
        row.setEntity(entity);
        row.setEntityId(entityId);
        row.setBeforeState(beforeJson);
        row.setAfterState(afterJson);
        row.setCorrelationId(CorrelationIds.current());
        row.setPrevHash(prev);
        row.setHash(hash);
        row.setCreatedAt(createdAt);
        logs.save(row);
        head.setHash(hash);
    }

    public AuditVerifyReport verify() {
        List<AuditLog> rows = logs.findAllByOrderByIdAsc();
        String prev = com.payflow.common.SystemAccounts.GENESIS_HASH;
        for (AuditLog row : rows) {
            if (!prev.equals(row.getPrevHash())) {
                return new AuditVerifyReport(false, row.getId(), "prev_hash does not match the previous row");
            }
            String expected = AuditHasher.hash(
                    row.getPrevHash(),
                    row.getActorId(),
                    row.getActorRole(),
                    row.getAction(),
                    row.getEntity(),
                    row.getEntityId(),
                    row.getBeforeState(),
                    row.getAfterState(),
                    row.getCreatedAt());
            if (!expected.equals(row.getHash())) {
                return new AuditVerifyReport(false, row.getId(), "hash does not match the row contents");
            }
            prev = row.getHash();
        }
        return new AuditVerifyReport(true, null, "chain intact");
    }

    public List<AuditLog> find(String entity, String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return logs.findAllByOrderByIdAsc();
        }
        return logs.findByEntityAndEntityIdOrderByIdAsc(entity, entityId);
    }

    public record AuditVerifyReport(boolean valid, Long brokenAtId, String message) {
    }
}
