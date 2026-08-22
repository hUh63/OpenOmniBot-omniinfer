import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:ui/theme/theme_context.dart';

import 'onboarding_common.dart';

/// Flat, borderless selectable row — the card-free building block for the
/// distribution, preset, and provider pickers.
///
/// Selection is expressed with an animated accent tint plus an animated
/// check icon instead of a bordered card.
class OnboardingOptionRow extends StatelessWidget {
  const OnboardingOptionRow({
    super.key,
    required this.tapKey,
    required this.leading,
    required this.title,
    required this.selected,
    required this.onTap,
    this.badge,
    this.description,
    this.detail,
    this.showSelectionIndicator = true,
  });

  /// Key applied to the tappable area (kept stable for tests).
  final Key tapKey;
  final Widget leading;
  final String title;
  final String? badge;
  final String? description;
  final String? detail;
  final bool selected;
  final VoidCallback? onTap;
  final bool showSelectionIndicator;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final semanticsLabel = description == null ? title : '$title, $description';
    return Semantics(
      button: true,
      selected: selected,
      label: semanticsLabel,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          key: tapKey,
          onTap: onTap,
          borderRadius: BorderRadius.circular(16),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOutCubic,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
            decoration: BoxDecoration(
              color: selected
                  ? palette.accentPrimary.withValues(alpha: 0.08)
                  : Colors.transparent,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                leading,
                const SizedBox(width: 13),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 2),
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: Theme.of(context).textTheme.titleSmall
                                  ?.copyWith(
                                    color: palette.textPrimary,
                                    fontWeight: FontWeight.w700,
                                  ),
                            ),
                          ),
                          if (badge != null) ...[
                            const SizedBox(width: 8),
                            OnboardingBadge(text: badge!, selected: selected),
                          ],
                        ],
                      ),
                      if (description != null) ...[
                        const SizedBox(height: 4),
                        Text(
                          description!,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(
                                color: palette.textSecondary,
                                height: 1.5,
                              ),
                        ),
                      ],
                      if (detail != null) ...[
                        const SizedBox(height: 7),
                        Row(
                          children: [
                            Icon(
                              selected
                                  ? LucideIcons.circleCheck
                                  : LucideIcons.hardDrive,
                              size: 14,
                              color: selected
                                  ? palette.accentPrimary
                                  : palette.textTertiary,
                            ),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                detail!,
                                style: Theme.of(context).textTheme.labelSmall
                                    ?.copyWith(
                                      color: selected
                                          ? palette.accentPrimary
                                          : palette.textTertiary,
                                      fontWeight: FontWeight.w600,
                                      height: 1.4,
                                    ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: showSelectionIndicator
                      ? AnimatedSwitcher(
                          duration: const Duration(milliseconds: 180),
                          transitionBuilder: (child, animation) =>
                              ScaleTransition(
                                scale: animation,
                                child: FadeTransition(
                                  opacity: animation,
                                  child: child,
                                ),
                              ),
                          child: Icon(
                            selected
                                ? LucideIcons.circleCheck
                                : LucideIcons.circle,
                            key: ValueKey<bool>(selected),
                            size: 20,
                            color: selected
                                ? palette.accentPrimary
                                : palette.borderStrong,
                          ),
                        )
                      : Icon(
                          LucideIcons.chevronRight,
                          size: 20,
                          color: palette.textTertiary,
                        ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Animated square icon surface used as the leading element of an
/// [OnboardingOptionRow].
class OnboardingOptionIcon extends StatelessWidget {
  const OnboardingOptionIcon({
    super.key,
    required this.icon,
    required this.selected,
    this.size = 40,
  });

  final IconData icon;
  final bool selected;
  final double size;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeOutCubic,
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: selected
            ? palette.accentPrimary
            : palette.accentPrimary.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(size * 0.3),
      ),
      alignment: Alignment.center,
      child: Icon(
        icon,
        size: size * 0.5,
        color: selected
            ? Theme.of(context).colorScheme.onPrimary
            : palette.accentPrimary,
      ),
    );
  }
}

/// Hairline separator used between flat option rows.
class OnboardingRowDivider extends StatelessWidget {
  const OnboardingRowDivider({super.key});

  @override
  Widget build(BuildContext context) {
    return Divider(
      height: 1,
      thickness: 1,
      indent: 12,
      endIndent: 12,
      color: context.omniPalette.borderSubtle,
    );
  }
}
