package com.payflow.request;

import com.payflow.auth.AppUser;
import com.payflow.auth.UserRepository;
import com.payflow.common.ErrorCode;
import com.payflow.common.IdGenerator;
import com.payflow.common.Money;
import com.payflow.common.PayflowException;
import com.payflow.transfer.TransferDtos;
import com.payflow.transfer.TransferService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MoneyRequestService {

    private final MoneyRequestRepository requests;
    private final UserRepository users;
    private final TransferService transfers;
    private final IdGenerator ids;
    private final Clock clock;

    public MoneyRequestService(
            MoneyRequestRepository requests,
            UserRepository users,
            TransferService transfers,
            IdGenerator ids,
            Clock clock) {
        this.requests = requests;
        this.users = users;
        this.transfers = transfers;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public List<MoneyRequest> create(UUID requesterId, String note, List<Share> shares) {
        if (shares == null || shares.isEmpty()) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY, "Add at least one share");
        }
        List<MoneyRequest> saved = new ArrayList<>();
        for (Share share : shares) {
            Money.inr(share.amountMinor());
            String email = share.email().trim().toLowerCase();
            users.findByEmail(email)
                    .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "No user for " + email));
            MoneyRequest row = new MoneyRequest();
            row.setId(ids.newId());
            row.setRequesterId(requesterId);
            row.setPayerEmail(email);
            row.setAmountMinor(share.amountMinor());
            row.setNote(note);
            row.setStatus("OPEN");
            row.setCreatedAt(clock.instant());
            saved.add(requests.save(row));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MoneyRequest> list(UUID userId, String email) {
        return requests.findByRequesterIdOrPayerEmailOrderByCreatedAtDesc(userId, email.toLowerCase());
    }

    @Transactional
    public TransferDtos.TransferResponse pay(UUID payerId, UUID requestId, String key, String hash) {
        MoneyRequest row = requests.findById(requestId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Request not found"));
        AppUser payer = users.findById(payerId)
                .orElseThrow(() -> new PayflowException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required"));
        if (!payer.getEmail().equalsIgnoreCase(row.getPayerEmail())) {
            throw new PayflowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "This request is for someone else");
        }
        if (!"OPEN".equals(row.getStatus())) {
            throw new PayflowException(ErrorCode.ALREADY_PAID, HttpStatus.CONFLICT, "Request is already settled");
        }
        AppUser requester = users.findById(row.getRequesterId())
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Requester not found"));
        row.setStatus("PAID");
        return transfers.transfer(
                payerId,
                key,
                hash,
                new TransferDtos.TransferRequest(requester.getEmail(), row.getAmountMinor(), row.getNote()));
    }

    public record Share(String email, long amountMinor) {
    }
}
