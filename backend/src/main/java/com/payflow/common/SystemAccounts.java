package com.payflow.common;

import java.util.UUID;

public final class SystemAccounts {

    public static final UUID GATEWAY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID FEE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID ADMIN_USER = UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private SystemAccounts() {
    }
}
