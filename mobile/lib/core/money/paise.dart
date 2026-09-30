/// Rupee text and paise integers. Parsing uses integer math only.
class Paise {
  const Paise._();

  static String format(int minor) {
    final negative = minor < 0;
    final abs = minor.abs();
    final rupees = abs ~/ 100;
    final fraction = (abs % 100).toString().padLeft(2, '0');
    final grouped = _group(rupees.toString());
    final text = '₹$grouped.$fraction';
    return negative ? '-$text' : text;
  }

  /// Accepts `12`, `12.5`, and `12.50`. More than two decimal places is rejected.
  static int? parse(String raw) {
    final text = raw.trim().replaceAll(',', '').replaceAll('₹', '');
    if (text.isEmpty) {
      return null;
    }
    final negative = text.startsWith('-');
    final body = negative ? text.substring(1) : text;
    if (body.isEmpty || !RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(body)) {
      return null;
    }
    final parts = body.split('.');
    final rupees = int.parse(parts[0]);
    var fraction = 0;
    if (parts.length == 2) {
      final digits = parts[1].padRight(2, '0');
      fraction = int.parse(digits);
    }
    final minor = rupees * 100 + fraction;
    if (minor <= 0) {
      return null;
    }
    return negative ? -minor : minor;
  }

  static String _group(String digits) {
    if (digits.length <= 3) {
      return digits;
    }
    final head = digits.length - 3;
    final buffer = StringBuffer();
    final lead = head % 2;
    if (lead != 0) {
      buffer.write(digits.substring(0, lead));
    }
    for (var i = lead; i < head; i += 2) {
      if (buffer.isNotEmpty) {
        buffer.write(',');
      }
      buffer.write(digits.substring(i, i + 2));
    }
    if (buffer.isNotEmpty) {
      buffer.write(',');
    }
    buffer.write(digits.substring(head));
    return buffer.toString();
  }
}
