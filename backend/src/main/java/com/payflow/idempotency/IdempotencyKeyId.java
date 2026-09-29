package com.payflow.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class IdempotencyKeyId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "key", nullable = false, length = 100)
    private String keyValue;

    public IdempotencyKeyId() {
    }

    public IdempotencyKeyId(UUID userId, String keyValue) {
        this.userId = userId;
        this.keyValue = keyValue;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getKeyValue() {
        return keyValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdempotencyKeyId that)) {
            return false;
        }
        return userId.equals(that.userId) && keyValue.equals(that.keyValue);
    }

    @Override
    public int hashCode() {
        return userId.hashCode() * 31 + keyValue.hashCode();
    }
}
