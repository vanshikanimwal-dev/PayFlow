import 'package:drift/drift.dart';

import '../network/models.dart';
import 'app_database.dart';
import 'local_cache.dart';

class DriftCache implements LocalCache {
  DriftCache(this._db);

  final AppDatabase _db;

  @override
  Future<void> deleteAttempt(String idempotencyKey) {
    return (_db.delete(_db.attemptRows)..where((row) => row.idempotencyKey.equals(idempotencyKey))).go();
  }

  @override
  Future<List<AttemptRecord>> readAttempts() async {
    final rows = await _db.select(_db.attemptRows).get();
    return rows
        .map((row) => AttemptRecord(
              idempotencyKey: row.idempotencyKey,
              kind: row.kind,
              bodyJson: row.bodyJson,
              createdAt: row.createdAt,
            ))
        .toList();
  }

  @override
  Future<List<TxSummary>> readTransactions() async {
    final rows = await (_db.select(_db.transactionRows)..orderBy([(row) => OrderingTerm.desc(row.createdAt)])).get();
    return rows
        .map((row) => TxSummary(
              id: row.id,
              type: row.type,
              status: row.status,
              amountMinor: row.amountMinor,
              currency: row.currency,
              createdAt: row.createdAt,
            ))
        .toList();
  }

  @override
  Future<WalletSnapshot?> readWallet() async {
    final row = await _db.select(_db.walletRows).getSingleOrNull();
    if (row == null) {
      return null;
    }
    return WalletSnapshot(accountId: row.accountId, balanceMinor: row.balanceMinor, currency: row.currency);
  }

  @override
  Future<void> saveAttempt(AttemptRecord attempt) {
    return _db.into(_db.attemptRows).insertOnConflictUpdate(AttemptRowsCompanion.insert(
          idempotencyKey: attempt.idempotencyKey,
          kind: attempt.kind,
          bodyJson: attempt.bodyJson,
          createdAt: attempt.createdAt,
        ));
  }

  @override
  Future<void> saveTransactions(List<TxSummary> items) async {
    await _db.delete(_db.transactionRows).go();
    await _db.batch((batch) {
      batch.insertAll(
        _db.transactionRows,
        items
            .map((item) => TransactionRowsCompanion.insert(
                  id: item.id,
                  type: item.type,
                  status: item.status,
                  amountMinor: item.amountMinor,
                  currency: item.currency,
                  createdAt: item.createdAt,
                ))
            .toList(),
      );
    });
  }

  @override
  Future<void> saveWallet(WalletSnapshot wallet) {
    return _db.into(_db.walletRows).insertOnConflictUpdate(WalletRowsCompanion.insert(
          accountId: wallet.accountId,
          balanceMinor: wallet.balanceMinor,
          currency: wallet.currency,
        ));
  }
}
