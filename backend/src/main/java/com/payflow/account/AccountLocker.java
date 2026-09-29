package com.payflow.account;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccountLocker {

    List<Account> lock(Collection<UUID> ids);

    static Account require(List<Account> locked, UUID id) {
        for (Account account : locked) {
            if (account.getId().equals(id)) {
                return account;
            }
        }
        throw new IllegalStateException("Locked account missing: " + id);
    }
}
