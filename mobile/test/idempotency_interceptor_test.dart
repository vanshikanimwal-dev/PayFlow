import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:payflow_mobile/core/network/idempotency_interceptor.dart';

void main() {
  test('a money attempt reuses the same idempotency key', () async {
    final dio = Dio(BaseOptions(baseUrl: 'http://localhost'));
    dio.interceptors.add(IdempotencyInterceptor());
    dio.httpClientAdapter = _CaptureAdapter();
    await dio.post('/transfers', options: Options(extra: {IdempotencyInterceptor.extraKey: 'attempt-12345678'}));
    final captured = dio.httpClientAdapter as _CaptureAdapter;
    expect(captured.headers['Idempotency-Key'], 'attempt-12345678');
  });

  test('requests without an attempt key do not invent one', () async {
    final dio = Dio(BaseOptions(baseUrl: 'http://localhost'));
    dio.interceptors.add(IdempotencyInterceptor());
    dio.httpClientAdapter = _CaptureAdapter();
    await dio.get('/wallet');
    final captured = dio.httpClientAdapter as _CaptureAdapter;
    expect(captured.headers.containsKey('Idempotency-Key'), isFalse);
  });
}

class _CaptureAdapter implements HttpClientAdapter {
  Map<String, dynamic> headers = {};

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    headers = options.headers;
    return ResponseBody.fromString('{}', 200, headers: {
      Headers.contentTypeHeader: ['application/json'],
    });
  }
}
