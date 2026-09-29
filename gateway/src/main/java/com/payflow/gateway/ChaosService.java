package com.payflow.gateway;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class ChaosService {

    private volatile ChaosConfig config = new ChaosConfig();

    public ChaosConfig get() {
        return config;
    }

    public void replace(ChaosConfig next) {
        if (next.getLatencyMs() == null) {
            next.setLatencyMs(new ChaosConfig.Latency(0, 0));
        }
        if (next.getDelayedWebhookMs() == null) {
            next.setDelayedWebhookMs(new ChaosConfig.Latency(0, 0));
        }
        this.config = next;
    }

    public void latency() {
        ChaosConfig.Latency latency = config.getLatencyMs();
        int sleep = between(latency.min(), latency.max());
        if (sleep > 0) {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean timeout() {
        return chance(config.getTimeoutRate());
    }

    public boolean error() {
        return chance(config.getErrorRate());
    }

    public boolean dropWebhook() {
        return chance(config.getDropWebhookRate());
    }

    public int duplicateCount() {
        return chance(config.getDuplicateWebhookRate()) ? 2 + ThreadLocalRandom.current().nextInt(2) : 1;
    }

    public int webhookDelayMs() {
        ChaosConfig.Latency delay = config.getDelayedWebhookMs();
        return between(delay.min(), delay.max());
    }

    public boolean drift() {
        return chance(config.getSettlementDriftRate());
    }

    private static boolean chance(double rate) {
        return rate > 0 && ThreadLocalRandom.current().nextDouble() < rate;
    }

    private static int between(int min, int max) {
        if (max <= min) {
            return Math.max(0, min);
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
