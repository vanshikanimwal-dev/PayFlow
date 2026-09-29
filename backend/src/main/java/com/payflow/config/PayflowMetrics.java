package com.payflow.config;

import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransactionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PayflowMetrics {

    public PayflowMetrics(MeterRegistry registry, WalletTransactionRepository transactions) {
        Gauge.builder("topups_pending", transactions,
                        repository -> repository.countByTypeAndStatus(TransactionType.TOPUP, TransactionStatus.PROCESSING))
                .register(registry);
    }
}
