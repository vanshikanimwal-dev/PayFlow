import '../network/models.dart';

abstract class LocalCache {
  Future<void> saveWallet(WalletSnapshot wallet);
  Future<WalletSnapshot?> readWallet();
  Future<void> saveTransactions(List<TxSummary> items);
  Future<List<TxSummary>> readTransactions();
  Future<void> saveAttempt(AttemptRecord attempt);
  Future<List<AttemptRecord>> readAttempts();
  Future<void> deleteAttempt(String idempotencyKey);
}

class MemoryCache implements LocalCache {
  WalletSnapshot? _wallet;
  final List<TxSummary> _transactions = [];
  final Map<String, AttemptRecord> _attempts = {};

  @override
  Future<void> deleteAttempt(String idempotencyKey) async {
    _attempts.remove(idempotencyKey);
  }

  @override
  Future<List<AttemptRecord>> readAttempts() async => _attempts.values.toList();

  @override
  Future<List<TxSummary>> readTransactions() async => List.unmodifiable(_transactions);

  @override
  Future<WalletSnapshot?> readWallet() async => _wallet;

  @override
  Future<void> saveAttempt(AttemptRecord attempt) async {
    _attempts[attempt.idempotencyKey] = attempt;
  }

  @override
  Future<void> saveTransactions(List<TxSummary> items) async {
    _transactions
      ..clear()
      ..addAll(items);
  }

  @override
  Future<void> saveWallet(WalletSnapshot wallet) async {
    _wallet = wallet;
  }
}
