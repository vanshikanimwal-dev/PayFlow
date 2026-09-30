import 'package:flutter_test/flutter_test.dart';
import 'package:payflow_mobile/features/history/statement.dart';

void main() {
  test('statement pdf starts with the pdf header', () {
    final bytes = statementPdf(statementText(['2026-09-30  TRANSFER  COMPLETED  ₹20.00']));
    final header = String.fromCharCodes(bytes.take(8));
    expect(header, '%PDF-1.4');
  });
}
