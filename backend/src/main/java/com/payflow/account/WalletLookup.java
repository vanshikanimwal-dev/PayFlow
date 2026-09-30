package com.payflow.account;

import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The spending wallet is the USER_WALLET, or the MERCHANT account when the user has no personal wallet.
 * Savings is a second account and must never be returned here.
 */
@Component
public class WalletLookup {

    private final AccountRepository accounts;

    public WalletLookup(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Account spending(UUID userId) {
        return accounts.findByOwnerIdAndType(userId, AccountType.USER_WALLET)
                .or(() -> accounts.findByOwnerIdAndType(userId, AccountType.MERCHANT))
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    public Optional<Account> savings(UUID userId) {
        return accounts.findByOwnerIdAndType(userId, AccountType.SAVINGS);
    }

    public List<Account> owned(UUID userId) {
        List<Account> rows = accounts.findAllByOwnerId(userId);
        if (rows.isEmpty()) {
            throw new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Wallet not found");
        }
        return rows;
    }
}
