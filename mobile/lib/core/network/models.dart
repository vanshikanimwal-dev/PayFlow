class AuthResult {
  AuthResult({required this.accessToken, required this.refreshToken, required this.expiresIn});

  final String accessToken;
  final String refreshToken;
  final int expiresIn;

  factory AuthResult.fromJson(Map<String, dynamic> json) {
    return AuthResult(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      expiresIn: (json['expiresIn'] as num).toInt(),
    );
  }
}

class WalletSnapshot {
  WalletSnapshot({required this.accountId, required this.balanceMinor, required this.currency});

  final String accountId;
  final int balanceMinor;
  final String currency;

  factory WalletSnapshot.fromJson(Map<String, dynamic> json) {
    return WalletSnapshot(
      accountId: json['accountId'] as String,
      balanceMinor: (json['balanceMinor'] as num).toInt(),
      currency: json['currency'] as String? ?? 'INR',
    );
  }
}

class TxSummary {
  TxSummary({
    required this.id,
    required this.type,
    required this.status,
    required this.amountMinor,
    required this.currency,
    required this.createdAt,
  });

  final String id;
  final String type;
  final String status;
  final int amountMinor;
  final String currency;
  final DateTime createdAt;

  factory TxSummary.fromJson(Map<String, dynamic> json) {
    return TxSummary(
      id: json['id'] as String,
      type: json['type'] as String? ?? '',
      status: json['status'] as String? ?? '',
      amountMinor: (json['amountMinor'] as num).toInt(),
      currency: json['currency'] as String? ?? 'INR',
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}

class TxPage {
  TxPage({required this.items, this.nextCursor});

  final List<TxSummary> items;
  final String? nextCursor;

  factory TxPage.fromJson(Map<String, dynamic> json) {
    final raw = json['items'] as List<dynamic>? ?? const [];
    return TxPage(
      items: raw.map((item) => TxSummary.fromJson(item as Map<String, dynamic>)).toList(),
      nextCursor: json['nextCursor'] as String?,
    );
  }
}

class LedgerLine {
  LedgerLine({
    required this.accountId,
    required this.direction,
    required this.amountMinor,
    required this.balanceAfter,
  });

  final String accountId;
  final String direction;
  final int amountMinor;
  final int balanceAfter;

  factory LedgerLine.fromJson(Map<String, dynamic> json) {
    return LedgerLine(
      accountId: json['accountId'] as String,
      direction: json['direction'] as String? ?? '',
      amountMinor: (json['amountMinor'] as num).toInt(),
      balanceAfter: (json['balanceAfter'] as num).toInt(),
    );
  }
}

class TxDetail {
  TxDetail({
    required this.id,
    required this.type,
    required this.status,
    required this.amountMinor,
    required this.currency,
    required this.createdAt,
    required this.refundedMinor,
    required this.entries,
  });

  final String id;
  final String type;
  final String status;
  final int amountMinor;
  final String currency;
  final DateTime createdAt;
  final int refundedMinor;
  final List<LedgerLine> entries;

  factory TxDetail.fromJson(Map<String, dynamic> json) {
    final raw = json['entries'] as List<dynamic>? ?? const [];
    return TxDetail(
      id: json['id'] as String,
      type: json['type'] as String? ?? '',
      status: json['status'] as String? ?? '',
      amountMinor: (json['amountMinor'] as num).toInt(),
      currency: json['currency'] as String? ?? 'INR',
      createdAt: DateTime.parse(json['createdAt'] as String),
      refundedMinor: (json['refundedMinor'] as num?)?.toInt() ?? 0,
      entries: raw.map((item) => LedgerLine.fromJson(item as Map<String, dynamic>)).toList(),
    );
  }
}

class TransferResult {
  TransferResult({required this.transactionId, required this.status, required this.balanceAfterMinor});

  final String transactionId;
  final String status;
  final int balanceAfterMinor;

  factory TransferResult.fromJson(Map<String, dynamic> json) {
    return TransferResult(
      transactionId: json['transactionId'] as String,
      status: json['status'] as String? ?? '',
      balanceAfterMinor: (json['balanceAfterMinor'] as num).toInt(),
    );
  }
}

class TopUpResult {
  TopUpResult({required this.transactionId, required this.status, this.paymentUrl});

  final String transactionId;
  final String status;
  final String? paymentUrl;

  factory TopUpResult.fromJson(Map<String, dynamic> json) {
    return TopUpResult(
      transactionId: json['transactionId'] as String,
      status: json['status'] as String? ?? '',
      paymentUrl: json['paymentUrl'] as String?,
    );
  }
}

class PaymentRequestView {
  PaymentRequestView({
    required this.id,
    required this.qrPayload,
    required this.expiresAt,
    required this.status,
    required this.amountMinor,
    this.description,
  });

  final String id;
  final String qrPayload;
  final DateTime expiresAt;
  final String status;
  final int amountMinor;
  final String? description;

  factory PaymentRequestView.fromJson(Map<String, dynamic> json) {
    return PaymentRequestView(
      id: json['id'] as String,
      qrPayload: json['qrPayload'] as String? ?? '',
      expiresAt: DateTime.parse(json['expiresAt'] as String),
      status: json['status'] as String? ?? '',
      amountMinor: (json['amountMinor'] as num).toInt(),
      description: json['description'] as String?,
    );
  }
}

class RefundResult {
  RefundResult({required this.transactionId, required this.status, required this.refundedMinor});

  final String transactionId;
  final String status;
  final int refundedMinor;

  factory RefundResult.fromJson(Map<String, dynamic> json) {
    return RefundResult(
      transactionId: json['transactionId'] as String,
      status: json['status'] as String? ?? '',
      refundedMinor: (json['refundedMinor'] as num).toInt(),
    );
  }
}

class AttemptRecord {
  AttemptRecord({required this.idempotencyKey, required this.kind, required this.bodyJson, required this.createdAt});

  final String idempotencyKey;
  final String kind;
  final String bodyJson;
  final DateTime createdAt;
}
