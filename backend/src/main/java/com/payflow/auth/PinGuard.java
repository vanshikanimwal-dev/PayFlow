package com.payflow.auth;

import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PinGuard {

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public PinGuard(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    /**
     * A pin is required only after the user has set one. A locked wallet blocks every money movement.
     */
    public void require(UUID userId, String pin) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new PayflowException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required"));
        if (user.isWalletLocked()) {
            throw new PayflowException(ErrorCode.WALLET_LOCKED, HttpStatus.UNPROCESSABLE_ENTITY, "Wallet is locked");
        }
        if (user.getPinHash() == null) {
            return;
        }
        if (pin == null || pin.isBlank()) {
            throw new PayflowException(ErrorCode.PIN_REQUIRED, HttpStatus.UNPROCESSABLE_ENTITY, "Transaction PIN is required");
        }
        if (!passwords.matches(pin, user.getPinHash())) {
            throw new PayflowException(ErrorCode.PIN_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, "Transaction PIN is incorrect");
        }
    }
}
