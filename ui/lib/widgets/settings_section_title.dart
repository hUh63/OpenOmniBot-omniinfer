import 'package:flutter/material.dart';
import 'package:ui/l10n/l10n.dart';
import 'package:ui/theme/theme_context.dart';

class SettingsSectionTitle extends StatelessWidget {
  const SettingsSectionTitle({
    super.key,
    required this.label,
    this.subtitle,
    this.trailing,
    this.topPadding = 0,
    this.bottomPadding = 10,
  });

  final String label;
  final String? subtitle;
  final Widget? trailing;
  final double topPadding;
  final double bottomPadding;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final titleWidget = Text(
      context.trLegacy(label),
      style: TextStyle(
        fontSize: 12,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.3,
        color: palette.textTertiary,
        height: 1.35,
      ),
    );
    return Padding(
      padding: EdgeInsets.fromLTRB(4, topPadding, 4, bottomPadding),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (trailing == null)
            titleWidget
          else
            Row(
              children: [
                Expanded(child: titleWidget),
                const SizedBox(width: 8),
                trailing!,
              ],
            ),
          if (subtitle != null) ...[
            const SizedBox(height: 4),
            Text(
              context.trLegacy(subtitle!),
              style: TextStyle(
                fontSize: 12,
                height: 1.48,
                color: palette.textSecondary,
                fontWeight: FontWeight.w400,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
