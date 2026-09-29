package com.payflow.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 40)
    private String entity;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private String afterState;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public void setBeforeState(String beforeState) {
        this.beforeState = beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public void setAfterState(String afterState) {
        this.afterState = afterState;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getPrevHash() {
        return prevHash == null ? null : prevHash.strip();
    }

    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }

    public String getHash() {
        return hash == null ? null : hash.strip();
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
