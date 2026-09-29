package com.payflow.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final ObjectMapper mapper;
    private final ChaosService chaos;
    private final String secret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public WebhookDispatcher(ObjectMapper mapper, ChaosService chaos, @Value("${payflow.gateway.hmac-secret}") String secret) {
        this.mapper = mapper;
        this.chaos = chaos;
        this.secret = secret;
    }

    public void send(String callbackUrl, String type, GatewayPaymentEntity payment) {
        if (callbackUrl == null || callbackUrl.isBlank() || chaos.dropWebhook()) {
            return;
        }
        int copies = chaos.duplicateCount();
        int delay = chaos.webhookDelayMs();
        for (int i = 0; i < copies; i++) {
            scheduler.schedule(() -> post(callbackUrl, type, payment), delay + (long) i * 50, TimeUnit.MILLISECONDS);
        }
        if (chaos.get().isOutOfOrderWebhooks() && "payment.succeeded".equals(type)) {
            scheduler.schedule(() -> post(callbackUrl, "payment.failed", payment), Math.max(0, delay / 2), TimeUnit.MILLISECONDS);
        }
    }

    private void post(String callbackUrl, String type, GatewayPaymentEntity payment) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "eventId", "evt_" + payment.getId() + "_" + type + "_" + System.nanoTime(),
                    "type", type,
                    "paymentId", payment.getId(),
                    "amountMinor", payment.getAmountMinor(),
                    "reference", payment.getReference(),
                    "createdAt", Instant.now().toString()));
            long timestamp = Instant.now().getEpochSecond();
            String signature = hmac(timestamp + "." + body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(callbackUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Gateway-Signature", "t=" + timestamp + ",v1=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            log.warn("Webhook delivery failed for {}", payment.getId(), ex);
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String hmac(String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
}
