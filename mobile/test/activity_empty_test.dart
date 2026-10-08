import 'package:flutter_test/flutter_test.dart';
import 'package:payflow_mobile/features/history/history_screen.dart';

void main() {
  test('a new wallet does not look like a failed search', () {
    expect(
      activityEmptyLine(loadedAny: false, filtered: false),
      'No payments yet. Add money from the wallet screen.',
    );
  });

  test('a real filter still says nothing matched', () {
    expect(
      activityEmptyLine(loadedAny: true, filtered: true),
      'Nothing matches this filter.',
    );
  });
}
