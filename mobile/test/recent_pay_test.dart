import 'package:flutter_test/flutter_test.dart';
import 'package:payflow_mobile/core/theme/ui_prefs.dart';

void main() {
  test('a payment in the last ten minutes is called out', () {
    final warning = recentPayWarning(
      pays: [
        RecentPay(recipient: 'Bob@payflow.local', amountMinor: 2000, at: DateTime.utc(2026, 10, 8, 6, 55)),
      ],
      recipient: 'bob@payflow.local',
      now: DateTime.utc(2026, 10, 8, 7),
    );
    expect(warning, 'You already sent ₹20.00 to this person. Send again only if you mean to.');
  });

  test('an older payment stays quiet', () {
    final warning = recentPayWarning(
      pays: [
        RecentPay(recipient: 'bob@payflow.local', amountMinor: 2000, at: DateTime.utc(2026, 10, 8, 6)),
      ],
      recipient: 'bob@payflow.local',
      now: DateTime.utc(2026, 10, 8, 7),
    );
    expect(warning, isNull);
  });
}
