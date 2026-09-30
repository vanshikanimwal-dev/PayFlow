import 'dart:convert';

class JwtPayload {
  JwtPayload({required this.userId, required this.email, required this.role});

  final String userId;
  final String email;
  final String role;

  static JwtPayload decode(String token) {
    final parts = token.split('.');
    if (parts.length < 2) {
      throw const FormatException('Token is not a JWT');
    }
    final normalized = base64Url.normalize(parts[1]);
    final map = jsonDecode(utf8.decode(base64Url.decode(normalized))) as Map<String, dynamic>;
    return JwtPayload(
      userId: map['sub'] as String? ?? '',
      email: map['email'] as String? ?? '',
      role: map['role'] as String? ?? 'USER',
    );
  }
}
