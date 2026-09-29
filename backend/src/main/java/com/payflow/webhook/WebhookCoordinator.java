package com.payflow.webhook;

import com.payflow.common.OptimisticRetry;
import com.payflow.config.PayflowProperties;
import org.springframework.stereotype.Service;

@Service
public class WebhookCoordinator {

    private final GatewaySignatureVerifier verifier;
    private final WebhookService webhooks;
    private final OptimisticRetry optimisticRetry;
    private final PayflowProperties properties;

    public WebhookCoordinator(
            GatewaySignatureVerifier verifier,
            WebhookService webhooks,
            OptimisticRetry optimisticRetry,
            PayflowProperties properties) {
        this.verifier = verifier;
        this.webhooks = webhooks;
        this.optimisticRetry = optimisticRetry;
        this.properties = properties;
    }

    public void handle(byte[] body, String signatureHeader) {
        verifier.verify(signatureHeader, body);
        if (properties.getLocking().optimistic()) {
            optimisticRetry.run(() -> {
                webhooks.handle(body);
                return null;
            });
        } else {
            webhooks.handle(body);
        }
    }
}
