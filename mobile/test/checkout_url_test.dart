import 'package:flutter_test/flutter_test.dart';
import 'package:payflow_mobile/features/topup/topup_screen.dart';

void main() {
  test('checkout on a phone uses the computer address, not localhost', () {
    expect(
      checkoutUrl('http://localhost:8081/pay/pi_1', 'http://192.168.1.3:8080/api/v1'),
      'http://192.168.1.3:8081/pay/pi_1',
    );
  });
}
