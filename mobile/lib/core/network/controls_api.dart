abstract class ControlsApi {
  Future<Map<String, dynamic>> me();
  Future<void> setPin(String pin);
  Future<Map<String, dynamic>> setupTotp();
  Future<void> confirmTotp(String code);
  Future<List<dynamic>> sessions();
  Future<void> revokeSession(String id);
  Future<Map<String, dynamic>> lockWallet(bool locked);
  Future<Map<String, dynamic>> openSavings();
  Future<Map<String, dynamic>> moveSavings({required String idempotencyKey, required bool toSavings, required int amountMinor});
  Future<List<dynamic>> notifications();
  Future<void> markRead(String id);
  Future<Map<String, dynamic>> schedule({required String toEmail, required int amountMinor, String? note, required int dayOfMonth});
  Future<List<dynamic>> schedules();
  Future<List<dynamic>> createRequests({String? note, required List<Map<String, dynamic>> shares});
  Future<List<dynamic>> requests();
  Future<Map<String, dynamic>> payRequest({required String id, required String idempotencyKey});
  Future<List<dynamic>> fraudFlags();
  Future<Map<String, dynamic>> quote(int amountMinor);
  Future<void> dispute({required String transactionId, required String note});
}
