package com.payflow.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @Size(max = 20) String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull UserRole role) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, String code) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresIn) {
    }
}
