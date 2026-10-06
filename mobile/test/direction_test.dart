import 'package:flutter_test/flutter_test.dart';
import 'package:payflow_mobile/core/network/models.dart';

void main() {
  test('money lands in the wallet that is the destination', () {
    expect(
      moneyComingIn(type: 'PAYMENT', fromAccountId: 'shop', toAccountId: 'me', mine: 'me'),
      isTrue,
    );
    expect(
      moneyComingIn(type: 'TRANSFER', fromAccountId: 'me', toAccountId: 'friend', mine: 'me'),
      isFalse,
    );
  });

  test('top up counts as money in when the accounts are unknown', () {
    expect(moneyComingIn(type: 'TOPUP', mine: null), isTrue);
    expect(moneyComingIn(type: 'PAYMENT', mine: null), isFalse);
  });
}
