package com.payflow.notify;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ControlsDtos {

    private ControlsDtos() {
    }

    public record PinRequest(@NotBlank @Pattern(regexp = "\\d{4,6}") String pin) {
    }

    public record CodeRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    public record TotpSetup(String secret, String otpauth) {
    }

    public record LockRequest(boolean locked) {
    }

    public record Profile(
            String email,
            String displayName,
            boolean walletLocked,
            boolean pinSet,
            boolean totpEnabled,
            Long savingsMinor) {
    }

    public record SessionView(UUID id, String deviceLabel, Instant createdAt, boolean revoked) {
    }

    public record NotificationView(UUID id, String title, String body, boolean read, Instant createdAt) {
    }

    public record ScheduleRequest(
            @NotBlank String toEmail,
            @Positive long amountMinor,
            @Size(max = 255) String note,
            @Min(1) @Max(28) int dayOfMonth) {
    }

    public record ScheduleView(UUID id, String toEmail, long amountMinor, String note, int dayOfMonth, LocalDate nextRunOn, boolean active) {
    }

    public record ShareLine(@NotBlank String email, @Positive long amountMinor) {
    }

    public record SplitRequest(@Size(max = 255) String note, @NotEmpty List<ShareLine> shares) {
    }

    public record RequestView(UUID id, String payerEmail, long amountMinor, String note, String status, Instant createdAt, boolean incoming) {
    }

    public record MoveRequest(boolean toSavings, @Positive long amountMinor) {
    }

    public record FraudView(UUID id, UUID userId, String kind, String detail, Instant createdAt, boolean open) {
    }
}
