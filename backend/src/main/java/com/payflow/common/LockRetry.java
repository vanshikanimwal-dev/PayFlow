package com.payflow.common;

import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Retries a whole transaction after a lock timeout or deadlock. Callers must invoke this
 * from outside the {@code @Transactional} method so each attempt starts a new transaction.
 */
@Service
public class LockRetry {

    @Retryable(
            retryFor = {
                PessimisticLockingFailureException.class,
                OptimisticLockingFailureException.class,
                TransientLockException.class
            },
            noRetryFor = PayflowException.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 10, multiplier = 2))
    public <T> T run(Supplier<T> work) {
        try {
            return work.get();
        } catch (PayflowException ex) {
            if (ex.code() == ErrorCode.LOCK_TIMEOUT) {
                throw new TransientLockException(ex);
            }
            throw ex;
        }
    }

    @Recover
    public <T> T recover(TransientLockException ex, Supplier<T> work) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw ex;
    }

    public static final class TransientLockException extends RuntimeException {
        public TransientLockException(PayflowException cause) {
            super(cause);
        }
    }
}
