import 'package:flutter_test/flutter_test.dart';
import 'package:payflow_mobile/core/network/models.dart';
import 'package:payflow_mobile/core/theme/ui_prefs.dart';
import 'package:payflow_mobile/features/history/history_screen.dart';
import 'package:payflow_mobile/features/wallet/home_screen.dart';

void main() {
  test('greeting follows the time of day', () {
    expect(timeGreeting(DateTime(2026, 10, 8, 9)), 'Good morning');
    expect(timeGreeting(DateTime(2026, 10, 8, 14)), 'Good afternoon');
    expect(timeGreeting(DateTime(2026, 10, 8, 20)), 'Good evening');
  });

  test('monthly room left is whole rupees', () {
    expect(monthLeftLine(spent: 20000, limit: 500000), '₹4,800.00 left this month');
    expect(monthLeftLine(spent: 600000, limit: 500000), '₹1,000.00 over this month');
  });

  test('activity groups today and yesterday under one heading each', () {
    final now = DateTime(2026, 10, 8, 18);
    final rows = activityRows([
      _tx('a', DateTime(2026, 10, 8, 12)),
      _tx('b', DateTime(2026, 10, 8, 9)),
      _tx('c', DateTime(2026, 10, 7, 21)),
    ], now);
    expect(rows.map((row) => row.label ?? row.item!.id).toList(), ['Today', 'a', 'b', 'Yesterday', 'c']);
  });
}

TxSummary _tx(String id, DateTime at) {
  return TxSummary(id: id, type: 'TOPUP', status: 'COMPLETED', amountMinor: 100, currency: 'INR', createdAt: at);
}
