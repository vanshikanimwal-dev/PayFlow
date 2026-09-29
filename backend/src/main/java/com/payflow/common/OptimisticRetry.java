package com.payflow.common;

import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class OptimisticRetry {

    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 8,
            backoff = @Backoff(delay = 15, multiplier = 2, maxDelay = 200))
    public <T> T run(Supplier<T> work) {
        return work.get();
    }
}
