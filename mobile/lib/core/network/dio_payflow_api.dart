import 'package:dio/dio.dart';

import '../auth/token_store.dart';
import '../errors/api_exception.dart';
import 'auth_interceptor.dart';
import 'correlation_interceptor.dart';
import 'idempotency_interceptor.dart';
import 'controls_api.dart';
import 'models.dart';
import 'payflow_api.dart';

const defaultApiBase = String.fromEnvironment('API_BASE', defaultValue: 'http://localhost:8080/api/v1');

class DioPayflowApi implements PayflowApi, ControlsApi {
  DioPayflowApi({required TokenStore tokens, String baseUrl = defaultApiBase, Dio? dio})
      : _dio = dio ??
            Dio(BaseOptions(
              baseUrl: baseUrl,
              connectTimeout: const Duration(seconds: 10),
              receiveTimeout: const Duration(seconds: 15),
              headers: {'Content-Type': 'application/json'},
            )) {
    final replay = Dio(BaseOptions(
      baseUrl: _dio.options.baseUrl,
      connectTimeout: _dio.options.connectTimeout,
      receiveTimeout: _dio.options.receiveTimeout,
      headers: {'Content-Type': 'application/json'},
    ));
    replay.interceptors.add(CorrelationInterceptor());
    _dio.interceptors.add(CorrelationInterceptor());
    _dio.interceptors.add(IdempotencyInterceptor());
    _dio.interceptors.add(AuthInterceptor(
      tokens: tokens,
      refresh: () => _refresh(tokens, replay),
      replay: (options) => replay.fetch(options),
    ));
  }

  final Dio _dio;

  static Future<void> _refresh(TokenStore tokens, Dio replay) async {
    final stored = await tokens.read();
    if (stored == null) {
      throw ApiException(401, 'UNAUTHENTICATED', 'Sign in again');
    }
    final response = await replay.post<Map<String, dynamic>>(
      '/auth/refresh',
      data: {'refreshToken': stored.refreshToken},
      options: Options(extra: {'skipAuth': true}),
    );
    final auth = AuthResult.fromJson(response.data ?? {});
    await tokens.write(StoredTokens(accessToken: auth.accessToken, refreshToken: auth.refreshToken));
  }

  @override
  Future<AuthResult> login({required String email, required String password, String? code}) {
    return _data(
      _dio.post('/auth/login',
          data: {
            'email': email,
            'password': password,
            if (code != null && code.isNotEmpty) 'code': code,
          },
          options: Options(extra: {'skipAuth': true})),
      AuthResult.fromJson,
    );
  }

  @override
  Future<AuthResult> register({
    required String email,
    String? phone,
    required String password,
    required String role,
  }) {
    return _data(
      _dio.post('/auth/register',
          data: {
            'email': email,
            if (phone != null && phone.isNotEmpty) 'phone': phone,
            'password': password,
            'role': role,
          },
          options: Options(extra: {'skipAuth': true})),
      AuthResult.fromJson,
    );
  }

  @override
  Future<void> logout(String refreshToken) async {
    await _send(_dio.post('/auth/logout', data: {'refreshToken': refreshToken}));
  }

  @override
  Future<WalletSnapshot> wallet() => _data(_dio.get('/wallet'), WalletSnapshot.fromJson);

  @override
  Future<TxPage> transactions({String? cursor, String? type, String? status}) {
    final query = <String, dynamic>{'limit': 20};
    if (cursor != null) {
      query['cursor'] = cursor;
    }
    if (type != null && type.isNotEmpty) {
      query['type'] = type;
    }
    if (status != null && status.isNotEmpty) {
      query['status'] = status;
    }
    return _data(_dio.get('/wallet/transactions', queryParameters: query), TxPage.fromJson);
  }

  @override
  Future<TxDetail> transaction(String id) => _data(_dio.get('/transactions/$id'), TxDetail.fromJson);

  @override
  Future<TxDetail> byKey(String idempotencyKey) =>
      _data(_dio.get('/transactions/by-key/$idempotencyKey'), TxDetail.fromJson);

  @override
  Future<TransferResult> transfer({
    required String idempotencyKey,
    required String toUserEmailOrPhone,
    required int amountMinor,
    String? note,
  }) {
    return _data(
      _dio.post('/transfers',
          data: {
            'toUserEmailOrPhone': toUserEmailOrPhone,
            'amountMinor': amountMinor,
            if (note != null && note.isNotEmpty) 'note': note,
          },
          options: Options(extra: {IdempotencyInterceptor.extraKey: idempotencyKey})),
      TransferResult.fromJson,
    );
  }

  @override
  Future<TopUpResult> startTopUp({
    required String idempotencyKey,
    required int amountMinor,
    required String method,
  }) {
    return _data(
      _dio.post('/topups',
          data: {'amountMinor': amountMinor, 'method': method},
          options: Options(extra: {IdempotencyInterceptor.extraKey: idempotencyKey})),
      TopUpResult.fromJson,
    );
  }

  @override
  Future<TopUpResult> topUpStatus(String transactionId) =>
      _data(_dio.get('/topups/$transactionId'), TopUpResult.fromJson);

  @override
  Future<PaymentRequestView> createPaymentRequest({
    required int amountMinor,
    String? description,
  }) {
    return _data(
      _dio.post('/merchant/payment-requests', data: {
        'amountMinor': amountMinor,
        if (description != null && description.isNotEmpty) 'description': description,
      }),
      PaymentRequestView.fromJson,
    );
  }

  @override
  Future<PaymentRequestView> paymentRequest(String id) =>
      _data(_dio.get('/merchant/payment-requests/$id'), PaymentRequestView.fromJson);

  @override
  Future<RefundResult> refund({
    required String idempotencyKey,
    required String transactionId,
    required int amountMinor,
  }) {
    return _data(
      _dio.post('/refunds',
          data: {'transactionId': transactionId, 'amountMinor': amountMinor},
          options: Options(extra: {IdempotencyInterceptor.extraKey: idempotencyKey})),
      RefundResult.fromJson,
    );
  }

  @override
  Future<TransferResult> pay({required String idempotencyKey, required String paymentRequestId}) {
    return _data(
      _dio.post('/payments',
          data: {'paymentRequestId': paymentRequestId},
          options: Options(extra: {IdempotencyInterceptor.extraKey: idempotencyKey})),
      TransferResult.fromJson,
    );
  }

  @override
  Future<List<Map<String, dynamic>>> adminTransactions({String? status}) async {
    final data = await _send(_dio.get('/admin/transactions', queryParameters: {
      if (status != null && status.isNotEmpty) 'status': status,
    }));
    return (data as List<dynamic>).cast<Map<String, dynamic>>();
  }

  @override
  Future<List<Map<String, dynamic>>> reconciliationRuns() async {
    final data = await _send(_dio.get('/admin/reconciliation/runs'));
    return (data as List<dynamic>).cast<Map<String, dynamic>>();
  }

  @override
  Future<List<Map<String, dynamic>>> reconciliationItems(String runId) async {
    final data = await _send(_dio.get('/admin/reconciliation/runs/$runId/items'));
    return (data as List<dynamic>).cast<Map<String, dynamic>>();
  }

  @override
  Future<Map<String, dynamic>> runReconciliation() async {
    final data = await _send(_dio.post('/admin/reconciliation/run'));
    return data as Map<String, dynamic>;
  }

  @override
  Future<Map<String, dynamic>> integrity() async {
    final data = await _send(_dio.get('/admin/integrity'));
    return data as Map<String, dynamic>;
  }

  @override
  Future<Map<String, dynamic>> verifyAudit() async {
    final data = await _send(_dio.get('/admin/audit/verify'));
    return data as Map<String, dynamic>;
  }

  @override
  Future<Map<String, dynamic>> me() async => (await _send(_dio.get('/me'))) as Map<String, dynamic>;

  @override
  Future<void> setPin(String pin) async {
    await _send(_dio.post('/auth/pin', data: {'pin': pin}));
  }

  @override
  Future<Map<String, dynamic>> setupTotp() async => (await _send(_dio.post('/auth/2fa/setup'))) as Map<String, dynamic>;

  @override
  Future<void> confirmTotp(String code) async {
    await _send(_dio.post('/auth/2fa/confirm', data: {'code': code}));
  }

  @override
  Future<List<dynamic>> sessions() async => (await _send(_dio.get('/auth/sessions'))) as List<dynamic>;

  @override
  Future<void> revokeSession(String id) async {
    await _send(_dio.post('/auth/sessions/$id/revoke'));
  }

  @override
  Future<Map<String, dynamic>> lockWallet(bool locked) async =>
      (await _send(_dio.post('/wallet/lock', data: {'locked': locked}))) as Map<String, dynamic>;

  @override
  Future<Map<String, dynamic>> openSavings() async => (await _send(_dio.post('/wallet/savings'))) as Map<String, dynamic>;

  @override
  Future<Map<String, dynamic>> moveSavings({required String idempotencyKey, required bool toSavings, required int amountMinor}) async {
    return (await _send(_dio.post('/wallet/savings/move',
        data: {'toSavings': toSavings, 'amountMinor': amountMinor},
        options: Options(extra: {IdempotencyInterceptor.extraKey: idempotencyKey})))) as Map<String, dynamic>;
  }

  @override
  Future<List<dynamic>> notifications() async => (await _send(_dio.get('/notifications'))) as List<dynamic>;

  @override
  Future<void> markRead(String id) async {
    await _send(_dio.post('/notifications/$id/read'));
  }

  @override
  Future<Map<String, dynamic>> schedule({required String toEmail, required int amountMinor, String? note, required int dayOfMonth}) async {
    return (await _send(_dio.post('/schedules', data: {
      'toEmail': toEmail,
      'amountMinor': amountMinor,
      if (note != null && note.isNotEmpty) 'note': note,
      'dayOfMonth': dayOfMonth,
    }))) as Map<String, dynamic>;
  }

  @override
  Future<List<dynamic>> schedules() async => (await _send(_dio.get('/schedules'))) as List<dynamic>;

  @override
  Future<List<dynamic>> createRequests({String? note, required List<Map<String, dynamic>> shares}) async {
    return (await _send(_dio.post('/requests', data: {
      if (note != null && note.isNotEmpty) 'note': note,
      'shares': shares,
    }))) as List<dynamic>;
  }

  @override
  Future<List<dynamic>> requests() async => (await _send(_dio.get('/requests'))) as List<dynamic>;

  @override
  Future<Map<String, dynamic>> payRequest({required String id, required String idempotencyKey}) async {
    return (await _send(_dio.post('/requests/$id/pay', options: Options(extra: {IdempotencyInterceptor.extraKey: idempotencyKey}))))
        as Map<String, dynamic>;
  }

  @override
  Future<List<dynamic>> fraudFlags() async => (await _send(_dio.get('/admin/fraud'))) as List<dynamic>;

  @override
  Future<Map<String, dynamic>> quote(int amountMinor) async {
    return (await _send(_dio.get('/payments/quote', queryParameters: {'amountMinor': amountMinor}))) as Map<String, dynamic>;
  }

  @override
  Future<void> dispute({required String transactionId, required String note}) async {
    await _send(_dio.post('/disputes', data: {'transactionId': transactionId, 'note': note}));
  }

  Future<T> _data<T>(Future<Response<dynamic>> call, T Function(Map<String, dynamic>) parse) async {
    final data = await _send(call);
    return parse(data as Map<String, dynamic>);
  }

  Future<dynamic> _send(Future<Response<dynamic>> call) async {
    try {
      final response = await call;
      return response.data;
    } on DioException catch (error) {
      if (error.type == DioExceptionType.connectionTimeout ||
          error.type == DioExceptionType.receiveTimeout ||
          error.type == DioExceptionType.sendTimeout) {
        throw const PayflowTimeout();
      }
      final data = error.response?.data;
      if (data is Map && data['code'] is String) {
        throw ApiException(
          error.response?.statusCode ?? 0,
          data['code'] as String,
          data['message'] as String? ?? '',
        );
      }
      throw ApiException(error.response?.statusCode ?? 0, 'NETWORK', error.message ?? 'Request failed');
    }
  }
}
