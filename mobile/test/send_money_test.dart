import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:payflow_mobile/core/app_scope.dart';
import 'package:payflow_mobile/core/errors/api_exception.dart';
import 'package:payflow_mobile/core/local/local_cache.dart';
import 'package:payflow_mobile/core/network/models.dart';
import 'package:payflow_mobile/core/network/payflow_api.dart';
import 'package:payflow_mobile/core/theme/ui_prefs.dart';
import 'package:payflow_mobile/features/transfer/send_screen.dart';

void main() {
  testWidgets('send money shows success', (tester) async {
    final api = _FakeApi();
    await tester.pumpWidget(_app(api));
    await tester.enterText(find.byKey(const Key('recipient')), 'bob@payflow.local');
    await tester.enterText(find.byKey(const Key('amount')), '20');
    await tester.tap(find.byKey(const Key('review')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('confirm')));
    await tester.pump();
    await tester.pump();
    expect(find.textContaining('is with Bob'), findsOneWidget);
    expect(api.transferCalls, 1);
  });

  testWidgets('insufficient balance offers a top up', (tester) async {
    final api = _FakeApi()..error = ApiException(422, 'INSUFFICIENT_BALANCE', 'Wallet balance is too low');
    await tester.pumpWidget(_app(api));
    await tester.enterText(find.byKey(const Key('recipient')), 'bob@payflow.local');
    await tester.enterText(find.byKey(const Key('amount')), '20');
    await tester.tap(find.byKey(const Key('review')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('confirm')));
    await tester.pump();
    await tester.pump();
    expect(find.text('Top up'), findsOneWidget);
  });

  testWidgets('a timeout stays on checking status', (tester) async {
    final api = _FakeApi()..timeout = true;
    await tester.pumpWidget(_app(api));
    await tester.enterText(find.byKey(const Key('recipient')), 'bob@payflow.local');
    await tester.enterText(find.byKey(const Key('amount')), '20');
    await tester.tap(find.byKey(const Key('review')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('confirm')));
    await tester.pump();
    expect(find.textContaining('Do not pay again'), findsOneWidget);
  });

  testWidgets('confirm names the person and warns about a recent payment', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        payflowApiProvider.overrideWithValue(_FakeApi()),
        localCacheProvider.overrideWithValue(MemoryCache()),
        uiPrefsProvider.overrideWith(_RecentPrefs.new),
      ],
      child: const MaterialApp(home: SendScreen()),
    ));
    await tester.enterText(find.byKey(const Key('recipient')), 'bob@payflow.local');
    await tester.enterText(find.byKey(const Key('amount')), '20');
    await tester.tap(find.byKey(const Key('review')));
    await tester.pumpAndSettle();
    expect(find.text('Bob'), findsOneWidget);
    expect(find.textContaining('already sent'), findsOneWidget);
  });
}

Widget _app(_FakeApi api) {
  return ProviderScope(
    overrides: [
      payflowApiProvider.overrideWithValue(api),
      localCacheProvider.overrideWithValue(MemoryCache()),
    ],
    child: const MaterialApp(home: SendScreen()),
  );
}

class _FakeApi implements PayflowApi {
  ApiException? error;
  bool timeout = false;
  int transferCalls = 0;

  @override
  Future<RecipientView> recipient(String emailOrPhone) async {
    return RecipientView(name: 'Bob', email: 'bob@payflow.local');
  }

  @override
  Future<TransferResult> transfer({
    required String idempotencyKey,
    required String toUserEmailOrPhone,
    required int amountMinor,
    String? note,
  }) async {
    transferCalls += 1;
    if (timeout) {
      throw const PayflowTimeout();
    }
    if (error != null) {
      throw error!;
    }
    return TransferResult(transactionId: 'tx', status: 'COMPLETED', balanceAfterMinor: 8000);
  }

  @override
  Future<TxDetail> byKey(String idempotencyKey) async {
    if (timeout) {
      return Completer<TxDetail>().future;
    }
    return TxDetail(
      id: 'tx',
      type: 'TRANSFER',
      status: 'COMPLETED',
      amountMinor: 2000,
      currency: 'INR',
      createdAt: DateTime.utc(2026, 1, 1),
      refundedMinor: 0,
      entries: const [],
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _RecentPrefs extends UiPrefs {
  @override
  UiState build() {
    return UiState(recent: [
      RecentPay(recipient: 'bob@payflow.local', amountMinor: 5000, at: DateTime.now().toUtc()),
    ]);
  }
}
