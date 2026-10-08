import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../money/paise.dart';

class RecentPay {
  const RecentPay({required this.recipient, required this.amountMinor, required this.at});

  final String recipient;
  final int amountMinor;
  final DateTime at;
}

class UiState {
  const UiState({
    this.dark = true,
    this.hideBalance = false,
    this.hindi = false,
    this.onboarded = false,
    this.displayName = '',
    this.favorites = const [],
    this.recent = const [],
  });

  final bool dark;
  final bool hideBalance;
  final bool hindi;
  final bool onboarded;
  final String displayName;
  final List<String> favorites;
  final List<RecentPay> recent;

  UiState copy({
    bool? dark,
    bool? hideBalance,
    bool? hindi,
    bool? onboarded,
    String? displayName,
    List<String>? favorites,
    List<RecentPay>? recent,
  }) {
    return UiState(
      dark: dark ?? this.dark,
      hideBalance: hideBalance ?? this.hideBalance,
      hindi: hindi ?? this.hindi,
      onboarded: onboarded ?? this.onboarded,
      displayName: displayName ?? this.displayName,
      favorites: favorites ?? this.favorites,
      recent: recent ?? this.recent,
    );
  }
}

class UiPrefs extends Notifier<UiState> {
  @override
  UiState build() => const UiState();

  void toggleDark() => state = state.copy(dark: !state.dark);
  void toggleBalance() => state = state.copy(hideBalance: !state.hideBalance);
  void toggleLanguage() => state = state.copy(hindi: !state.hindi);

  void finishOnboarding(String name) => state = state.copy(onboarded: true, displayName: name);

  void remember(String email) {
    final next = [email, ...state.favorites.where((item) => item != email)].take(6).toList();
    state = state.copy(favorites: next);
  }

  void rememberPayment(String recipient, int amountMinor) {
    final next = [
      RecentPay(recipient: recipient, amountMinor: amountMinor, at: DateTime.now().toUtc()),
      ...state.recent.where((item) => item.recipient.toLowerCase() != recipient.toLowerCase()),
    ].take(8).toList();
    state = state.copy(recent: next);
  }
}

String? recentPayWarning({
  required List<RecentPay> pays,
  required String recipient,
  required DateTime now,
}) {
  for (final pay in pays) {
    if (pay.recipient.toLowerCase() != recipient.toLowerCase()) {
      continue;
    }
    if (now.difference(pay.at) > const Duration(minutes: 10)) {
      continue;
    }
    return 'You already sent ${Paise.format(pay.amountMinor)} to this person. Send again only if you mean to.';
  }
  return null;
}

final uiPrefsProvider = NotifierProvider<UiPrefs, UiState>(UiPrefs.new);

String tr(bool hindi, String key) {
  const en = {
    'hello': 'Hello',
    'wallet': 'PayFlow wallet',
    'send': 'Send',
    'topup': 'Top up',
    'scan': 'Scan',
    'activity': 'Recent activity',
    'empty': 'No payments yet. Top up to get started.',
    'settings': 'Settings',
    'dark': 'Dark mode',
    'language': 'हिंदी',
    'inbox': 'Notifications',
    'more': 'Money tools',
  };
  const hi = {
    'hello': 'नमस्ते',
    'wallet': 'PayFlow वॉलेट',
    'send': 'भेजें',
    'topup': 'जोड़ें',
    'scan': 'स्कैन',
    'activity': 'हाल की गतिविधि',
    'empty': 'अभी कोई भुगतान नहीं। शुरू करने के लिए पैसे जोड़ें।',
    'settings': 'सेटिंग्स',
    'dark': 'डार्क मोड',
    'language': 'English',
    'inbox': 'सूचनाएँ',
    'more': 'पैसे के उपकरण',
  };
  final table = hindi ? hi : en;
  return table[key] ?? en[key] ?? key;
}
