package com.payflow.idempotency;

import com.payflow.common.ErrorCode;
import com.payflow.common.Jsons;
import com.payflow.common.PayflowException;
import com.payflow.config.PayflowProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");

    private final EntityManager entityManager;
    private final IdempotencyKeyRepository keys;
    private final Jsons jsons;
    private final Clock clock;
    private final PayflowProperties properties;
    private final MeterRegistry meters;

    public IdempotencyService(
            EntityManager entityManager,
            IdempotencyKeyRepository keys,
            Jsons jsons,
            Clock clock,
            PayflowProperties properties,
            MeterRegistry meters) {
        this.entityManager = entityManager;
        this.keys = keys;
        this.jsons = jsons;
        this.clock = clock;
        this.properties = properties;
        this.meters = meters;
    }

    public <T> BeginResult<T> begin(UUID userId, String key, String requestHash, Class<T> responseType) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new PayflowException(
                    ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Idempotency-Key must be 8-100 safe characters");
        }
        Instant now = clock.instant();
        entityManager.createNativeQuery("""
                        delete from idempotency_keys
                        where user_id = :userId and key = :key and expires_at < :now
                        """)
                .setParameter("userId", userId)
                .setParameter("key", key)
                .setParameter("now", now)
                .executeUpdate();
        int inserted = entityManager.createNativeQuery("""
                        insert into idempotency_keys
                          (user_id, key, request_hash, status, response_code, response_body, created_at, expires_at)
                        values (:userId, :key, :hash, 'IN_PROGRESS', null, null, :now, :expires)
                        on conflict (user_id, key) do nothing
                        """)
                .setParameter("userId", userId)
                .setParameter("key", key)
                .setParameter("hash", requestHash)
                .setParameter("now", now)
                .setParameter("expires", now.plus(properties.getIdempotency().getTtl()))
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        IdempotencyKey row = keys.findById(new IdempotencyKeyId(userId, key))
                .orElseThrow(() -> new IllegalStateException("Idempotency key disappeared"));
        if (!requestHash.equals(row.getRequestHash())) {
            throw new PayflowException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    HttpStatus.CONFLICT,
                    "Idempotency key was already used with a different request");
        }
        if (row.getStatus() == IdempotencyStatus.DONE) {
            meters.counter("idempotency_replays").increment();
            return new BeginResult.Replay<>(jsons.read(row.getResponseBody(), responseType));
        }
        if (inserted == 0) {
            Instant recoverAfter = now.minus(properties.getIdempotency().getInProgressRecoverAfter());
            if (row.getCreatedAt().isBefore(recoverAfter)) {
                return new BeginResult.Recover<>();
            }
            throw new PayflowException(
                    ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                    HttpStatus.CONFLICT,
                    "A request with this idempotency key is still running");
        }
        return new BeginResult.Proceed<>();
    }

    public void complete(UUID userId, String key, int statusCode, Object body) {
        IdempotencyKey row = keys.findById(new IdempotencyKeyId(userId, key))
                .orElseThrow(() -> new IllegalStateException("Idempotency key missing at completion"));
        row.setStatus(IdempotencyStatus.DONE);
        row.setResponseCode(statusCode);
        row.setResponseBody(jsons.write(body));
    }

    @org.springframework.transaction.annotation.Transactional
    public int deleteExpired() {
        return keys.deleteExpired(clock.instant());
    }
}
