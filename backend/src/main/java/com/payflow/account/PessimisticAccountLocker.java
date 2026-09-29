package com.payflow.account;

import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payflow.locking.strategy", havingValue = "pessimistic", matchIfMissing = true)
public class PessimisticAccountLocker implements AccountLocker {

    private final AccountRepository accounts;
    private final DbLockTimeout lockTimeout;

    public PessimisticAccountLocker(AccountRepository accounts, DbLockTimeout lockTimeout) {
        this.accounts = accounts;
        this.lockTimeout = lockTimeout;
    }

    @Override
    public List<Account> lock(Collection<UUID> ids) {
        List<UUID> sorted = distinctSorted(ids);
        lockTimeout.apply();
        try {
            List<Account> locked = accounts.lockByIds(sorted);
            if (locked.size() != sorted.size()) {
                throw new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Account not found");
            }
            return locked;
        } catch (PayflowException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            if (isLockTimeout(ex)) {
                throw new PayflowException(
                        ErrorCode.LOCK_TIMEOUT, HttpStatus.SERVICE_UNAVAILABLE, "Could not lock accounts; retry");
            }
            throw ex;
        }
    }

    static List<UUID> distinctSorted(Collection<UUID> ids) {
        List<UUID> sorted = ids.stream().distinct().sorted().toList();
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("No accounts to lock");
        }
        return sorted;
    }

    private static boolean isLockTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof PessimisticLockingFailureException) {
                return true;
            }
            if (current instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("lock timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
