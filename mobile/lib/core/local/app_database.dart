import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

part 'app_database.g.dart';

class WalletRows extends Table {
  TextColumn get accountId => text()();
  IntColumn get balanceMinor => integer()();
  TextColumn get currency => text()();

  @override
  Set<Column<Object>> get primaryKey => {accountId};
}

class TransactionRows extends Table {
  TextColumn get id => text()();
  TextColumn get type => text()();
  TextColumn get status => text()();
  IntColumn get amountMinor => integer()();
  TextColumn get currency => text()();
  DateTimeColumn get createdAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {id};
}

class AttemptRows extends Table {
  TextColumn get idempotencyKey => text()();
  TextColumn get kind => text()();
  TextColumn get bodyJson => text()();
  DateTimeColumn get createdAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {idempotencyKey};
}

@DriftDatabase(tables: [WalletRows, TransactionRows, AttemptRows])
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.executor);

  @override
  int get schemaVersion => 1;

  static Future<AppDatabase> open() async {
    final dir = await getApplicationSupportDirectory();
    final file = File(p.join(dir.path, 'payflow.sqlite'));
    return AppDatabase(NativeDatabase(file));
  }
}
