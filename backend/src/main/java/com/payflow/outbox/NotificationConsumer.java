package com.payflow.outbox;

import com.payflow.account.AccountRepository;
import com.payflow.auth.UserRepository;
import com.payflow.common.IdGenerator;
import com.payflow.notify.AppNotification;
import com.payflow.notify.NotificationRepository;
import com.payflow.transaction.WalletTransactionRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer implements OutboxConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final WalletTransactionRepository transactions;
    private final AccountRepository accounts;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final IdGenerator ids;
    private final Clock clock;

    public NotificationConsumer(
            WalletTransactionRepository transactions,
            AccountRepository accounts,
            UserRepository users,
            NotificationRepository notifications,
            IdGenerator ids,
            Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.users = users;
        this.notifications = notifications;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public void onEvent(OutboxEvent event) {
        transactions.findById(event.getAggregateId()).ifPresent(tx -> {
            String title = title(event.getEventType());
            String body = title + " · " + rupees(tx.getAmountMinor());
            notifyOwner(tx.getFromAccountId(), title, body);
            if (tx.getToAccountId() != null && !tx.getToAccountId().equals(tx.getFromAccountId())) {
                notifyOwner(tx.getToAccountId(), title, body);
            }
        });
    }

    private void notifyOwner(UUID accountId, String title, String body) {
        if (accountId == null) {
            return;
        }
        accounts.findById(accountId).ifPresent(account -> {
            if (account.getOwnerId() == null) {
                return;
            }
            AppNotification note = new AppNotification();
            note.setId(ids.newId());
            note.setUserId(account.getOwnerId());
            note.setTitle(title);
            note.setBody(body);
            note.setSeen(false);
            note.setCreatedAt(clock.instant());
            notifications.save(note);
            users.findById(account.getOwnerId()).ifPresent(user ->
                    log.info("receipt queued to {} — {}", user.getEmail(), body));
        });
    }

    private static String title(String eventType) {
        return switch (eventType) {
            case "TRANSFER_COMPLETED" -> "Transfer";
            case "PAYMENT_COMPLETED" -> "Payment";
            case "TOPUP_COMPLETED" -> "Top-up";
            case "REFUND_COMPLETED" -> "Refund";
            default -> "Activity";
        };
    }

    private static String rupees(long minor) {
        long whole = minor / 100;
        long frac = Math.abs(minor % 100);
        return "₹" + whole + "." + (frac < 10 ? "0" + frac : Long.toString(frac));
    }
}
