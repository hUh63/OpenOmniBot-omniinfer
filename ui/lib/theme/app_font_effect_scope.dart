import 'package:flutter/material.dart';
import 'package:ui/services/app_font_effect_service.dart';

class AppFontEffectScope extends StatelessWidget {
  const AppFontEffectScope.nonChat({super.key, required this.child})
    : enabled = true;

  final Widget child;
  final bool enabled;

  static FontWeight resolveNonChatWeight(
    BuildContext context,
    FontWeight weight,
  ) {
    if (!AppFontEffectService.isActive) {
      return weight;
    }
    return boostedWeight(weight);
  }

  static FontWeight boostedWeight(FontWeight weight) {
    if (weight.index <= FontWeight.w300.index) {
      return FontWeight.w400;
    }
    if (weight == FontWeight.w400) {
      return FontWeight.w500;
    }
    if (weight == FontWeight.w500) {
      return FontWeight.w600;
    }
    return weight;
  }

  static TextStyle? resolveNonChatTextStyle(
    BuildContext context,
    TextStyle? style,
  ) {
    if (!AppFontEffectService.isActive || style == null) {
      return style;
    }
    final weight = style.fontWeight;
    return style.copyWith(
      fontWeight: weight == null ? FontWeight.w500 : boostedWeight(weight),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (!enabled || !AppFontEffectService.isActive) {
      return child;
    }

    final baseTheme = Theme.of(context);
    final theme = baseTheme.copyWith(
      textTheme: _boostTextTheme(baseTheme.textTheme),
      primaryTextTheme: _boostTextTheme(baseTheme.primaryTextTheme),
      appBarTheme: baseTheme.appBarTheme.copyWith(
        titleTextStyle: resolveNonChatTextStyle(
          context,
          baseTheme.appBarTheme.titleTextStyle,
        ),
        toolbarTextStyle: resolveNonChatTextStyle(
          context,
          baseTheme.appBarTheme.toolbarTextStyle,
        ),
      ),
      inputDecorationTheme: _boostInputDecorationTheme(
        context,
        baseTheme.inputDecorationTheme,
      ),
      chipTheme: baseTheme.chipTheme.copyWith(
        labelStyle: resolveNonChatTextStyle(
          context,
          baseTheme.chipTheme.labelStyle,
        ),
        secondaryLabelStyle: resolveNonChatTextStyle(
          context,
          baseTheme.chipTheme.secondaryLabelStyle,
        ),
      ),
      listTileTheme: baseTheme.listTileTheme.copyWith(
        titleTextStyle: resolveNonChatTextStyle(
          context,
          baseTheme.listTileTheme.titleTextStyle,
        ),
        subtitleTextStyle: resolveNonChatTextStyle(
          context,
          baseTheme.listTileTheme.subtitleTextStyle,
        ),
        leadingAndTrailingTextStyle: resolveNonChatTextStyle(
          context,
          baseTheme.listTileTheme.leadingAndTrailingTextStyle,
        ),
      ),
    );

    return Theme(
      data: theme,
      child: DefaultTextStyle.merge(
        style: const TextStyle(fontWeight: FontWeight.w500),
        child: child,
      ),
    );
  }

  static TextTheme _boostTextTheme(TextTheme textTheme) {
    return textTheme.copyWith(
      displayLarge: _boostTextStyle(textTheme.displayLarge),
      displayMedium: _boostTextStyle(textTheme.displayMedium),
      displaySmall: _boostTextStyle(textTheme.displaySmall),
      headlineLarge: _boostTextStyle(textTheme.headlineLarge),
      headlineMedium: _boostTextStyle(textTheme.headlineMedium),
      headlineSmall: _boostTextStyle(textTheme.headlineSmall),
      titleLarge: _boostTextStyle(textTheme.titleLarge),
      titleMedium: _boostTextStyle(textTheme.titleMedium),
      titleSmall: _boostTextStyle(textTheme.titleSmall),
      bodyLarge: _boostTextStyle(textTheme.bodyLarge),
      bodyMedium: _boostTextStyle(textTheme.bodyMedium),
      bodySmall: _boostTextStyle(textTheme.bodySmall),
      labelLarge: _boostTextStyle(textTheme.labelLarge),
      labelMedium: _boostTextStyle(textTheme.labelMedium),
      labelSmall: _boostTextStyle(textTheme.labelSmall),
    );
  }

  static TextStyle? _boostTextStyle(TextStyle? style) {
    if (style == null) {
      return null;
    }
    final weight = style.fontWeight;
    return style.copyWith(
      fontWeight: weight == null ? FontWeight.w500 : boostedWeight(weight),
    );
  }

  static InputDecorationThemeData _boostInputDecorationTheme(
    BuildContext context,
    InputDecorationThemeData theme,
  ) {
    return theme.copyWith(
      hintStyle: resolveNonChatTextStyle(context, theme.hintStyle),
      labelStyle: resolveNonChatTextStyle(context, theme.labelStyle),
      helperStyle: resolveNonChatTextStyle(context, theme.helperStyle),
      errorStyle: resolveNonChatTextStyle(context, theme.errorStyle),
      prefixStyle: resolveNonChatTextStyle(context, theme.prefixStyle),
      suffixStyle: resolveNonChatTextStyle(context, theme.suffixStyle),
      counterStyle: resolveNonChatTextStyle(context, theme.counterStyle),
      floatingLabelStyle: resolveNonChatTextStyle(
        context,
        theme.floatingLabelStyle,
      ),
    );
  }
}
