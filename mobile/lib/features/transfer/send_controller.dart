import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../core/app_scope.dart';
import '../../core/errors/api_exception.dart';
import '../../core/money/paise.dart';
import '../../core/network/models.dart';
import '../../core/theme/ui_prefs.dart';

sealed class MoneyState {}

class MoneyIdle extends MoneyState {}

class MoneySubmitting extends MoneyState {}

class MoneyChecking extends MoneyState {
  MoneyChecking(this.message);
  final String message;
}

class MoneySuccess extends MoneyState {
  MoneySuccess(this.title, this.detail);
  final String title;
  final String detail;
}

class MoneyFailure extends MoneyState {
  MoneyFailure(this.code, this.message);
  final String code;
  final String message;
}

final sendControllerProvider = NotifierProvider<SendController, MoneyState>(SendController.new);

class SendController extends Notifier<MoneyState> {
  @override
  MoneyState build() => MoneyIdle();

  void reset() => state = MoneyIdle();

  Future<void> confirm({required String recipient, required String name, required int amountMinor, String? note}) async {
    final key = const Uuid().v4();
    final body = jsonEncode({'to': recipient, 'amountMinor': amountMinor, 'note': note});
    await ref.read(localCacheProvider).saveAttempt(AttemptRecord(
          idempotencyKey: key,
          kind: 'transfer',
          bodyJson: body,
          createdAt: DateTime.now().toUtc(),
        ));
    state = MoneySubmitting();
    try {
      final result = await ref.read(payflowApiProvider).transfer(
            idempotencyKey: key,
            toUserEmailOrPhone: recipient,
            amountMinor: amountMinor,
            note: note,
          );
      await ref.read(localCacheProvider).deleteAttempt(key);
      ref.read(uiPrefsProvider.notifier).remember(recipient);
      ref.read(uiPrefsProvider.notifier).rememberPayment(recipient, amountMinor);
      HapticFeedback.mediumImpact().catchError((_) {});
      state = MoneySuccess('Sent', '${Paise.format(amountMinor)} is with $name. You have ${Paise.format(result.balanceAfterMinor)} left.');
    } on PayflowTimeout {
      state = MoneyChecking('Still checking. Do not pay again.');
      await _poll(key);
    } on ApiException catch (error) {
      if (error.code != 'IDEMPOTENCY_IN_PROGRESS') {
        await ref.read(localCacheProvider).deleteAttempt(key);
      }
      final reason = error.message.isEmpty ? friendlyError(error.code) : error.message;
      state = MoneyFailure(error.code, 'Not sent. Your balance did not change. $reason');
    }
  }

  Future<void> resume(String key) async {
    state = MoneyChecking('Still checking. Do not pay again.');
    await _poll(key);
  }

  Future<void> _poll(String key) async {
    final deadline = DateTime.now().add(const Duration(seconds: 30));
    while (DateTime.now().isBefore(deadline)) {
      try {
        final detail = await ref.read(payflowApiProvider).byKey(key);
        await ref.read(localCacheProvider).deleteAttempt(key);
        state = MoneySuccess('Sent', '${Paise.format(detail.amountMinor)} went through. Do not pay again.');
        return;
      } on ApiException catch (error) {
        if (error.status != 404) {
          final reason = error.message.isEmpty ? friendlyError(error.code) : error.message;
          state = MoneyFailure(error.code, 'Not sent. Your balance did not change. $reason');
          return;
        }
      } on PayflowTimeout {
        // Keep polling until the deadline.
      }
      await Future<void>.delayed(const Duration(seconds: 2));
    }
    state = MoneyChecking('Still checking. Do not pay again.');
  }
}
