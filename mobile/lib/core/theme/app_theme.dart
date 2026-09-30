import 'package:flutter/material.dart';

class PayflowColors {
  static const ink = Color(0xFF1C1917);
  static const paper = Color(0xFFF6F3EC);
  static const card = Color(0xFFFFFCF7);
  static const green = Color(0xFF0F6E56);
  static const greenDeep = Color(0xFF0B3D32);
  static const gold = Color(0xFFC4A574);
  static const danger = Color(0xFF9F1239);
}

ThemeData payflowTheme() {
  final scheme = ColorScheme.fromSeed(seedColor: PayflowColors.green, brightness: Brightness.light).copyWith(
    primary: PayflowColors.green,
    onPrimary: Colors.white,
    surface: PayflowColors.paper,
    onSurface: PayflowColors.ink,
    error: PayflowColors.danger,
  );
  return ThemeData(
    colorScheme: scheme,
    scaffoldBackgroundColor: PayflowColors.paper,
    useMaterial3: true,
    appBarTheme: const AppBarTheme(
      backgroundColor: PayflowColors.paper,
      foregroundColor: PayflowColors.ink,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: PayflowColors.card,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: Color(0xFFE7E1D6))),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: Color(0xFFE7E1D6))),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: PayflowColors.green, width: 1.4)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        textStyle: const TextStyle(fontWeight: FontWeight.w600),
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: PayflowColors.card,
      indicatorColor: const Color(0xFFDCECE6),
      labelTextStyle: WidgetStateProperty.all(const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
    ),
  );
}

ThemeData payflowDarkTheme() {
  const background = Color(0xFF101614);
  const card = Color(0xFF1B2622);
  const line = Color(0xFF2E4038);
  const mint = Color(0xFF3DDC97);
  const ink = Color(0xFFF4F1EA);
  final scheme = ColorScheme.fromSeed(seedColor: PayflowColors.green, brightness: Brightness.dark).copyWith(
    primary: mint,
    onPrimary: const Color(0xFF05281C),
    surface: card,
    onSurface: ink,
    error: const Color(0xFFFF8A9A),
  );
  final border = OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: line));
  return ThemeData(
    colorScheme: scheme,
    scaffoldBackgroundColor: background,
    useMaterial3: true,
    brightness: Brightness.dark,
    appBarTheme: const AppBarTheme(
      backgroundColor: background,
      foregroundColor: ink,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: const Color(0xFF121C19),
      hintStyle: const TextStyle(color: Color(0xFF8AA399)),
      labelStyle: const TextStyle(color: Color(0xFF8AA399)),
      border: border,
      enabledBorder: border,
      focusedBorder: border.copyWith(borderSide: const BorderSide(color: mint, width: 1.4)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: mint,
        foregroundColor: const Color(0xFF05281C),
        minimumSize: const Size.fromHeight(52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: mint,
        minimumSize: const Size.fromHeight(48),
        side: const BorderSide(color: line),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(style: TextButton.styleFrom(foregroundColor: mint)),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith((states) => states.contains(WidgetState.selected) ? const Color(0xFF05281C) : ink),
      trackColor: WidgetStateProperty.resolveWith((states) => states.contains(WidgetState.selected) ? mint : line),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: card,
      indicatorColor: const Color(0xFF244038),
      labelTextStyle: WidgetStateProperty.all(const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: ink)),
    ),
    dividerColor: line,
  );
}
