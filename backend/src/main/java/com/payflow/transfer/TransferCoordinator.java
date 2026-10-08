package com.payflow.transfer;

import com.payflow.common.LockRetry;
import com.payflow.common.OptimisticRetry;
import com.payflow.common.RateLimiter;
import com.payflow.config.PayflowProperties;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TransferCoordinator {

    private final TransferService transfers;
    private final OptimisticRetry optimisticRetry;
    private final LockRetry lockRetry;
    private final PayflowProperties properties;
    private final RateLimiter rateLimiter;

    public TransferCoordinator(
            TransferService transfers,
            OptimisticRetry optimisticRetry,
            LockRetry lockRetry,
            PayflowProperties properties,
            RateLimiter rateLimiter) {
        this.transfers = transfers;
        this.optimisticRetry = optimisticRetry;
        this.lockRetry = lockRetry;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    public TransferDtos.RecipientView preview(String emailOrPhone) {
        return transfers.preview(emailOrPhone);
    }

    public TransferDtos.TransferResponse transfer(UUID userId, String key, String hash, TransferDtos.TransferRequest request) {
        rateLimiter.money(userId);
        if (properties.getLocking().optimistic()) {
            return optimisticRetry.run(() -> transfers.transfer(userId, key, hash, request));
        }
        return lockRetry.run(() -> transfers.transfer(userId, key, hash, request));
    }
}
