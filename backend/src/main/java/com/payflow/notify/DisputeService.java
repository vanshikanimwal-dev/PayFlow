package com.payflow.notify;

import com.payflow.common.ErrorCode;
import com.payflow.common.IdGenerator;
import com.payflow.common.PayflowException;
import com.payflow.transaction.TransactionQueryService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisputeService {

    private final DisputeRepository disputes;
    private final TransactionQueryService transactions;
    private final IdGenerator ids;
    private final Clock clock;

    public DisputeService(DisputeRepository disputes, TransactionQueryService transactions, IdGenerator ids, Clock clock) {
        this.disputes = disputes;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public DisputeView open(UUID userId, UUID transactionId, String note) {
        transactions.detail(userId, transactionId);
        if (disputes.existsByUserIdAndTransactionIdAndStatus(userId, transactionId, "OPEN")) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.CONFLICT, "This payment is already flagged");
        }
        Dispute dispute = new Dispute();
        dispute.setId(ids.newId());
        dispute.setUserId(userId);
        dispute.setTransactionId(transactionId);
        dispute.setNote(note.trim());
        dispute.setStatus("OPEN");
        dispute.setCreatedAt(clock.instant());
        disputes.save(dispute);
        return view(dispute);
    }

    @Transactional(readOnly = true)
    public List<DisputeView> mine(UUID userId) {
        return disputes.findByUserIdOrderByCreatedAtDesc(userId).stream().map(DisputeService::view).toList();
    }

    @Transactional(readOnly = true)
    public List<DisputeView> openOnes() {
        return disputes.findByStatusOrderByCreatedAtDesc("OPEN").stream().map(DisputeService::view).toList();
    }

    private static DisputeView view(Dispute dispute) {
        return new DisputeView(
                dispute.getId(), dispute.getUserId(), dispute.getTransactionId(), dispute.getNote(), dispute.getStatus(), dispute.getCreatedAt());
    }

    public record DisputeView(UUID id, UUID userId, UUID transactionId, String note, String status, java.time.Instant createdAt) {
    }
}
