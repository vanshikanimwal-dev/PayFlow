import 'models.dart';

abstract class PayflowApi {
  Future<AuthResult> register({
    required String email,
    String? phone,
    required String password,
    required String role,
  });

  Future<AuthResult> login({required String email, required String password, String? code});

  Future<void> logout(String refreshToken);

  Future<WalletSnapshot> wallet();

  Future<TxPage> transactions({String? cursor, String? type, String? status});

  Future<TxDetail> transaction(String id);

  Future<TxDetail> byKey(String idempotencyKey);

  Future<TransferResult> transfer({
    required String idempotencyKey,
    required String toUserEmailOrPhone,
    required int amountMinor,
    String? note,
  });

  Future<RecipientView> recipient(String emailOrPhone);

  Future<TopUpResult> startTopUp({
    required String idempotencyKey,
    required int amountMinor,
    required String method,
  });

  Future<TopUpResult> topUpStatus(String transactionId);

  Future<PaymentRequestView> createPaymentRequest({
    required int amountMinor,
    String? description,
  });

  Future<PaymentRequestView> paymentRequest(String id);

  Future<TransferResult> pay({required String idempotencyKey, required String paymentRequestId});

  Future<RefundResult> refund({
    required String idempotencyKey,
    required String transactionId,
    required int amountMinor,
  });

  Future<List<Map<String, dynamic>>> adminTransactions({String? status});

  Future<List<Map<String, dynamic>>> reconciliationRuns();

  Future<List<Map<String, dynamic>>> reconciliationItems(String runId);

  Future<Map<String, dynamic>> runReconciliation();

  Future<Map<String, dynamic>> integrity();

  Future<Map<String, dynamic>> verifyAudit();
}
