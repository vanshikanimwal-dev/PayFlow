package com.payflow.account;

import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payflow.locking.strategy", havingValue = "optimistic")
public class OptimisticAccountLocker implements AccountLocker {

    private final AccountRepository accounts;

    public OptimisticAccountLocker(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public List<Account> lock(Collection<UUID> ids) {
        List<UUID> sorted = PessimisticAccountLocker.distinctSorted(ids);
        List<Account> loaded = new ArrayList<>();
        for (UUID id : sorted) {
            loaded.add(accounts.findById(id).orElseThrow(() ->
                    new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Account not found")));
        }
        return loaded;
    }
}
