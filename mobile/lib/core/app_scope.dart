import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'auth/jwt_payload.dart';
import 'auth/token_store.dart';
import 'local/local_cache.dart';
import 'network/controls_api.dart';
import 'network/dio_payflow_api.dart';
import 'network/payflow_api.dart';

class Session {
  Session({required this.accessToken, required this.refreshToken, required this.email, required this.role, required this.userId});

  final String accessToken;
  final String refreshToken;
  final String email;
  final String role;
  final String userId;

  bool get isAdmin => role == 'ADMIN';
  bool get isMerchant => role == 'MERCHANT' || isAdmin;
}

final tokenStoreProvider = Provider<TokenStore>((ref) => SecureTokenStore());

final localCacheProvider = Provider<LocalCache>((ref) => MemoryCache());

final payflowApiProvider = Provider<PayflowApi>((ref) {
  return DioPayflowApi(tokens: ref.watch(tokenStoreProvider));
});

final controlsApiProvider = Provider<ControlsApi>((ref) => ref.watch(payflowApiProvider) as ControlsApi);

final sessionProvider = NotifierProvider<SessionController, Session?>(SessionController.new);

class SessionController extends Notifier<Session?> {
  @override
  Session? build() => null;

  Future<void> restore() async {
    final stored = await ref.read(tokenStoreProvider).read();
    if (stored == null) {
      state = null;
      return;
    }
    state = _session(stored);
  }

  Future<void> adopt(String accessToken, String refreshToken) async {
    final stored = StoredTokens(accessToken: accessToken, refreshToken: refreshToken);
    await ref.read(tokenStoreProvider).write(stored);
    state = _session(stored);
  }

  Future<void> logout() async {
    final current = state;
    state = null;
    if (current != null) {
      try {
        await ref.read(payflowApiProvider).logout(current.refreshToken);
      } catch (_) {
        // The local session is already cleared.
      }
    }
    await ref.read(tokenStoreProvider).clear();
  }

  Session _session(StoredTokens stored) {
    final payload = JwtPayload.decode(stored.accessToken);
    return Session(
      accessToken: stored.accessToken,
      refreshToken: stored.refreshToken,
      email: payload.email,
      role: payload.role,
      userId: payload.userId,
    );
  }
}

final onlineProvider = NotifierProvider<OnlineController, bool>(OnlineController.new);

class OnlineController extends Notifier<bool> {
  @override
  bool build() => true;

  void setOnline(bool online) => state = online;
}
