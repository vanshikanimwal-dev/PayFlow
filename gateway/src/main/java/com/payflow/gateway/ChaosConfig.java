package com.payflow.gateway;

public class ChaosConfig {

    private Latency latencyMs = new Latency(0, 0);
    private double timeoutRate;
    private double errorRate;
    private double duplicateWebhookRate;
    private Latency delayedWebhookMs = new Latency(0, 0);
    private boolean outOfOrderWebhooks;
    private double dropWebhookRate;
    private double settlementDriftRate;

    public Latency getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Latency latencyMs) {
        this.latencyMs = latencyMs;
    }

    public double getTimeoutRate() {
        return timeoutRate;
    }

    public void setTimeoutRate(double timeoutRate) {
        this.timeoutRate = timeoutRate;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public double getDuplicateWebhookRate() {
        return duplicateWebhookRate;
    }

    public void setDuplicateWebhookRate(double duplicateWebhookRate) {
        this.duplicateWebhookRate = duplicateWebhookRate;
    }

    public Latency getDelayedWebhookMs() {
        return delayedWebhookMs;
    }

    public void setDelayedWebhookMs(Latency delayedWebhookMs) {
        this.delayedWebhookMs = delayedWebhookMs;
    }

    public boolean isOutOfOrderWebhooks() {
        return outOfOrderWebhooks;
    }

    public void setOutOfOrderWebhooks(boolean outOfOrderWebhooks) {
        this.outOfOrderWebhooks = outOfOrderWebhooks;
    }

    public double getDropWebhookRate() {
        return dropWebhookRate;
    }

    public void setDropWebhookRate(double dropWebhookRate) {
        this.dropWebhookRate = dropWebhookRate;
    }

    public double getSettlementDriftRate() {
        return settlementDriftRate;
    }

    public void setSettlementDriftRate(double settlementDriftRate) {
        this.settlementDriftRate = settlementDriftRate;
    }

    public record Latency(int min, int max) {
    }
}
