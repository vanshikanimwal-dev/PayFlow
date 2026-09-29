package com.payflow.audit;

import com.payflow.common.CorrelationIds;
import com.payflow.common.Jsons;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final EntityManager entityManager;

    public AuditService(AuditLogRepository logs, AuditChainHeadRepository heads, Jsons jsons, Clock clock, EntityManager entityManager) {
        this.logs = logs;
        this.heads = heads;
        this.jsons = jsons;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional
    public void record(UUID actorId, String actorRole, String action, String entity, String entityId, Object before, Object after) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(894231)").getResultList();
        AuditChainHead head = heads.lockHead();
        entityManager.refresh(head, LockModeType.PESSIMISTIC_WRITE);
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        String beforeJson = jsonState(before);
        String afterJson = jsonState(after);
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
                    jsonState(row.getBeforeState()),
                    jsonState(row.getAfterState()),
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

    private String jsonState(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String raw) {
            if (raw.isBlank()) {
                return raw;
            }
            return jsons.canonical(jsons.read(raw, Object.class));
        }
        return jsons.canonical(value);
    }
}
