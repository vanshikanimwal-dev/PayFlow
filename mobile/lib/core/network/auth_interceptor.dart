import 'package:dio/dio.dart';

import '../auth/token_store.dart';

/// Adds the access token and, on `401 TOKEN_EXPIRED`, refreshes once and replays.
class AuthInterceptor extends Interceptor {
  AuthInterceptor({
    required this.tokens,
    required this.refresh,
    required this.replay,
  });

  final TokenStore tokens;
  final Future<void> Function() refresh;
  final Future<Response<dynamic>> Function(RequestOptions options) replay;
  Future<void>? _refreshing;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    if (options.extra['skipAuth'] == true) {
      handler.next(options);
      return;
    }
    final stored = await tokens.read();
    if (stored != null) {
      options.headers['Authorization'] = 'Bearer ${stored.accessToken}';
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    final code = _code(err);
    final retried = err.requestOptions.extra['retried'] == true;
    if (err.response?.statusCode == 401 && code == 'TOKEN_EXPIRED' && !retried) {
      try {
        _refreshing ??= refresh();
        await _refreshing;
        final stored = await tokens.read();
        final options = err.requestOptions;
        options.extra['retried'] = true;
        if (stored != null) {
          options.headers['Authorization'] = 'Bearer ${stored.accessToken}';
        }
        final response = await replay(options);
        handler.resolve(response);
        return;
      } catch (_) {
        await tokens.clear();
      } finally {
        _refreshing = null;
      }
    }
    handler.next(err);
  }

  static String? _code(DioException err) {
    final data = err.response?.data;
    if (data is Map && data['code'] is String) {
      return data['code'] as String;
    }
    return null;
  }
}
