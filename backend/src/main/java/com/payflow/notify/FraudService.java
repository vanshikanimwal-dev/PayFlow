package com.payflow.notify;

import com.payflow.common.IdGenerator;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FraudService {

    static final long LARGE_TRANSFER_MINOR = 1_000_000L;

    private final FraudFlagRepository flags;
    private final IdGenerator ids;
    private final Clock clock;

    public FraudService(FraudFlagRepository flags, IdGenerator ids, Clock clock) {
        this.flags = flags;
        this.ids = ids;
        this.clock = clock;
    }

    public void largeTransfer(UUID userId, long amountMinor) {
        if (amountMinor >= LARGE_TRANSFER_MINOR) {
            open(userId, "LARGE_TRANSFER", "Transfer of " + amountMinor + " paise");
        }
    }

    public void failedLogins(UUID userId, int count) {
        if (count >= 5) {
            open(userId, "FAILED_LOGINS", count + " failed logins");
        }
    }

    private void open(UUID userId, String kind, String detail) {
        FraudFlag flag = new FraudFlag();
        flag.setId(ids.newId());
        flag.setUserId(userId);
        flag.setKind(kind);
        flag.setDetail(detail);
        flag.setCreatedAt(clock.instant());
        flag.setOpen(true);
        flags.save(flag);
    }
}
