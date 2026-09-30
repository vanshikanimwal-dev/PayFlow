import 'package:flutter_test/flutter_test.dart';
import 'package:payflow_mobile/core/money/paise.dart';

void main() {
  test('formats paise with the Indian grouping', () {
    expect(Paise.format(100000), '₹1,000.00');
    expect(Paise.format(50), '₹0.50');
    expect(Paise.format(-250), '-₹2.50');
  });

  test('parses rupee text into paise with integer math', () {
    expect(Paise.parse('20'), 2000);
    expect(Paise.parse('20.5'), 2050);
    expect(Paise.parse('₹1,000.25'), 100025);
    expect(Paise.parse('0'), isNull);
    expect(Paise.parse('1.234'), isNull);
    expect(Paise.parse(''), isNull);
  });
}
