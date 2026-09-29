package com.payflow.webhook;

import com.payflow.common.ErrorCode;
import com.payflow.common.Hashes;
import com.payflow.common.PayflowException;
import com.payflow.config.PayflowProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GatewaySignatureVerifier {

    private final PayflowProperties properties;
    private final Clock clock;

    public GatewaySignatureVerifier(PayflowProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void verify(String header, byte[] body) {
        if (header == null || header.isBlank()) {
            throw denied("Missing gateway signature");
        }
        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0].trim())) {
                timestamp = pair[1].trim();
            } else if ("v1".equals(pair[0].trim())) {
                signature = pair[1].trim();
            }
        }
        if (timestamp == null || signature == null) {
            throw denied("Malformed gateway signature");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw denied("Malformed gateway signature");
        }
        if (Math.abs(clock.instant().getEpochSecond() - ts) > 300) {
            throw denied("Signature timestamp is outside the allowed window");
        }
        String payload = timestamp + "." + new String(body, StandardCharsets.UTF_8);
        String expected = Hashes.hmacSha256(properties.getGateway().getHmacSecret(), payload);
        if (!Hashes.constantTimeEquals(expected, signature.toLowerCase())) {
            throw denied("Invalid gateway signature");
        }
    }

    private static PayflowException denied(String message) {
        return new PayflowException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, message);
    }
}
