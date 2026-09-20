import 'package:flutter/material.dart';

ThemeData appTheme(Brightness brightness) {
  final scheme = ColorScheme.fromSeed(
    seedColor: const Color(0xff326b58),
    brightness: brightness,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: scheme.surface,
    appBarTheme: const AppBarTheme(
      centerTitle: false,
      scrolledUnderElevation: 0,
    ),
    cardTheme: CardThemeData(
      elevation: 0.5,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(minimumSize: const Size(0, 52)),
    ),
  );
}
