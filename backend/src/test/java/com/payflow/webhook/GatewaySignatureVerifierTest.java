package com.payflow.webhook;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payflow.common.Hashes;
import com.payflow.common.PayflowException;
import com.payflow.config.PayflowProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GatewaySignatureVerifierTest {

    private static final String SECRET = "test-gateway-hmac-secret";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void acceptsAFreshSignature() {
        GatewaySignatureVerifier verifier = verifier();
        byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        verifier.verify(header(NOW.getEpochSecond(), body), body);
    }

    @Test
    void rejectsAnOldTimestamp() {
        GatewaySignatureVerifier verifier = verifier();
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        long old = NOW.minusSeconds(301).getEpochSecond();
        assertThatThrownBy(() -> verifier.verify(header(old, body), body)).isInstanceOf(PayflowException.class);
    }

    @Test
    void rejectsATamperedBody() {
        GatewaySignatureVerifier verifier = verifier();
        byte[] body = "{\"amount\":1}".getBytes(StandardCharsets.UTF_8);
        String signature = header(NOW.getEpochSecond(), body);
        assertThatThrownBy(() -> verifier.verify(signature, "{\"amount\":2}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PayflowException.class);
    }

    private static GatewaySignatureVerifier verifier() {
        PayflowProperties properties = new PayflowProperties();
        properties.getGateway().setHmacSecret(SECRET);
        return new GatewaySignatureVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String header(long timestamp, byte[] body) {
        String payload = timestamp + "." + new String(body, StandardCharsets.UTF_8);
        return "t=" + timestamp + ",v1=" + Hashes.hmacSha256(SECRET, payload);
    }
}
