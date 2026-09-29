package com.payflow.transaction;

import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class TransactionCursor {

    private TransactionCursor() {
    }

    public record Decoded(Instant createdAt, UUID id) {
    }

    public static String encode(Instant createdAt, UUID id) {
        String raw = createdAt.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("shape");
            }
            return new Decoded(Instant.ofEpochMilli(Long.parseLong(parts[0])), UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Cursor is invalid");
        }
    }
}
