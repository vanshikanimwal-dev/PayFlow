import 'package:flutter_test/flutter_test.dart';
import 'package:payflow_mobile/features/merchant/scan_screen.dart';

void main() {
  test('reads a payment request id from a payflow link', () {
    expect(
      requestIdFrom('payflow://pay?requestId=11111111-1111-1111-1111-111111111111'),
      '11111111-1111-1111-1111-111111111111',
    );
  });
}
