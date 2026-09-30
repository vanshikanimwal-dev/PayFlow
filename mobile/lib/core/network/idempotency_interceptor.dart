import 'package:dio/dio.dart';

import '../auth/transaction_pin.dart';

/// Attaches the attempt key already chosen by the caller. A retry of the same
/// attempt reuses it; a new attempt puts a new key in [RequestOptions.extra].
class IdempotencyInterceptor extends Interceptor {
  static const extraKey = 'idempotencyKey';

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    apply(options);
    handler.next(options);
  }

  static void apply(RequestOptions options) {
    final key = options.extra[extraKey];
    if (key is String && key.isNotEmpty) {
      options.headers['Idempotency-Key'] = key;
    }
    final pin = TransactionPin.value;
    if (pin != null && pin.isNotEmpty) {
      options.headers['X-Transaction-Pin'] = pin;
    }
  }
}
