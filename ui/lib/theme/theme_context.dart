import 'package:flutter/material.dart';
import 'package:ui/theme/omni_theme_palette.dart';

extension OmniThemeContext on BuildContext {
  OmniThemePalette get omniPalette =>
      Theme.of(this).extension<OmniThemePalette>() ??
      (Theme.of(this).brightness == Brightness.dark
          ? OmniThemePalette.dark
          : OmniThemePalette.light);

  bool get isDarkTheme => Theme.of(this).brightness == Brightness.dark;

  TextStyle get omniInputTextStyle =>
      TextStyle(color: omniPalette.textPrimary, fontSize: 13);
}
