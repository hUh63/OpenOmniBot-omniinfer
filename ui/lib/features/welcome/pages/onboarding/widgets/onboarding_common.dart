import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:ui/theme/theme_context.dart';

/// Small pill used for inline labels such as "轻量推荐" / "仅保存在本机".
class OnboardingBadge extends StatelessWidget {
  const OnboardingBadge({super.key, required this.text, this.selected = false});

  final String text;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(
        color: selected
            ? palette.accentPrimary.withValues(alpha: 0.14)
            : palette.surfaceSecondary,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        text,
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
          color: selected ? palette.accentPrimary : palette.textSecondary,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

/// Error strip shown below forms and model sections.
class OnboardingInlineError extends StatelessWidget {
  const OnboardingInlineError({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final errorColor = Theme.of(context).colorScheme.error;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: errorColor.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: errorColor.withValues(alpha: 0.24)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(LucideIcons.circleAlert, size: 18, color: errorColor),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: context.omniPalette.textPrimary,
                height: 1.5,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Flat loading row shown while existing settings are being read.
class OnboardingLoadingRow extends StatelessWidget {
  const OnboardingLoadingRow({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 18),
      child: Row(
        children: [
          SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: palette.accentPrimary,
            ),
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Text(
              label,
              style: Theme.of(
                context,
              ).textTheme.bodyMedium?.copyWith(color: palette.textSecondary),
            ),
          ),
        ],
      ),
    );
  }
}

/// Full-width primary action used in the sticky footer slot.
class OnboardingPrimaryButton extends StatelessWidget {
  const OnboardingPrimaryButton({
    super.key,
    required this.buttonKey,
    required this.label,
    required this.icon,
    required this.onPressed,
  });

  /// Key applied to the inner [FilledButton] (kept stable for tests).
  final Key buttonKey;
  final String label;
  final IconData icon;
  final FutureOr<void> Function()? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 54,
      child: FilledButton.icon(
        key: buttonKey,
        onPressed: onPressed,
        icon: Icon(icon, size: 19),
        label: Text(label, style: const TextStyle(fontWeight: FontWeight.w700)),
        style: FilledButton.styleFrom(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
      ),
    );
  }
}

/// Shared input styling for the connection form and model fields.
InputDecoration onboardingInputDecoration(
  BuildContext context, {
  required String label,
  required String hint,
  required IconData icon,
  String? helper,
  Widget? suffix,
}) {
  final palette = context.omniPalette;
  final border = OutlineInputBorder(
    borderRadius: BorderRadius.circular(14),
    borderSide: BorderSide(color: palette.borderSubtle),
  );
  return InputDecoration(
    labelText: label,
    hintText: hint,
    helperText: helper,
    helperMaxLines: 2,
    prefixIcon: Icon(icon, size: 19),
    suffixIcon: suffix,
    filled: true,
    fillColor: palette.surfaceSecondary,
    labelStyle: TextStyle(color: palette.textSecondary),
    hintStyle: TextStyle(color: palette.textTertiary),
    helperStyle: TextStyle(color: palette.textTertiary, height: 1.4),
    prefixIconColor: palette.textSecondary,
    suffixIconColor: palette.textSecondary,
    enabledBorder: border,
    disabledBorder: border,
    border: border,
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(14),
      borderSide: BorderSide(color: palette.accentPrimary, width: 1.5),
    ),
    contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 17),
  );
}
