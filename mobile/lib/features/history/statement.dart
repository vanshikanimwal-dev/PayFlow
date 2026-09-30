String statementText(List<String> lines) {
  return ['PayFlow statement', ...lines].join('\n');
}

List<int> statementPdf(String text) {
  final lines = text.split('\n').take(40).toList();
  final buffer = StringBuffer('BT /F1 11 Tf 40 800 Td 14 TL ');
  for (final line in lines) {
    final safe = line.replaceAll('\\', r'\\').replaceAll('(', r'\(').replaceAll(')', r'\)');
    buffer.write('($safe) Tj T* ');
  }
  buffer.write('ET');
  final stream = buffer.toString();
  final objects = <String>[
    '1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n',
    '2 0 obj << /Type /Pages /Count 1 /Kids [3 0 R] >> endobj\n',
    '3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n',
    '4 0 obj << /Length ${stream.length} >> stream\n$stream\nendstream endobj\n',
    '5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n',
  ];
  final out = StringBuffer('%PDF-1.4\n');
  final offsets = <int>[0];
  for (final object in objects) {
    offsets.add(out.length);
    out.write(object);
  }
  final xref = out.length;
  out.write('xref\n0 ${objects.length + 1}\n');
  out.write('0000000000 65535 f \n');
  for (var i = 1; i < offsets.length; i++) {
    out.write('${offsets[i].toString().padLeft(10, '0')} 00000 n \n');
  }
  out.write('trailer << /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF');
  return out.toString().codeUnits;
}
