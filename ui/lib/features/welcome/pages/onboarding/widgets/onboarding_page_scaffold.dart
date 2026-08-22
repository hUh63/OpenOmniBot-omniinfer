import 'package:flutter/material.dart';
import 'package:ui/theme/theme_context.dart';

import 'onboarding_entrance.dart';

/// Leading icon + page title shown at the top of every tutorial page.
class OnboardingPageHeading extends StatelessWidget {
  const OnboardingPageHeading({
    super.key,
    required this.icon,
    required this.title,
  });

  final IconData icon;
  final String title;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Row(
      key: const ValueKey('tutorial-page-heading'),
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Container(
          key: const ValueKey('tutorial-page-leading-icon'),
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            color: palette.accentPrimary.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(16),
          ),
          alignment: Alignment.center,
          child: Icon(icon, size: 24, color: palette.accentPrimary),
        ),
        const SizedBox(width: 13),
        Expanded(
          child: Container(
            constraints: const BoxConstraints(minHeight: 48),
            alignment: Alignment.centerLeft,
            child: Text(
              title,
              key: const ValueKey('tutorial-page-title'),
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                color: palette.textPrimary,
                fontWeight: FontWeight.w800,
                letterSpacing: -0.35,
                height: 1.2,
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// Standard scrollable tutorial page: heading, description, then content
/// sections that cascade in with a staggered entrance.
class OnboardingPageScaffold extends StatelessWidget {
  const OnboardingPageScaffold({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
    required this.scrollController,
    required this.children,
  });

  final IconData icon;
  final String title;
  final String description;
  final ScrollController scrollController;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 760),
        child: SingleChildScrollView(
          controller: scrollController,
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              OnboardingPageHeading(icon: icon, title: title),
              const SizedBox(height: 10),
              OnboardingEntrance(
                index: 0,
                child: Text(
                  description,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: palette.textSecondary,
                    height: 1.65,
                  ),
                ),
              ),
              const SizedBox(height: 22),
              ...onboardingEntranceList(children, startIndex: 1),
            ],
          ),
        ),
      ),
    );
  }
}
