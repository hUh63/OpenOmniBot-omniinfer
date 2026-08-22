import 'package:flutter/material.dart';

/// One-shot staggered entrance animation (fade + slide up) for onboarding
/// content. The delay grows with [index] so sibling items cascade in.
///
/// Honors `MediaQuery.disableAnimations` and never loops, so it always
/// settles.
class OnboardingEntrance extends StatelessWidget {
  const OnboardingEntrance({super.key, required this.index, required this.child});

  final int index;
  final Widget child;

  static const Duration _totalDuration = Duration(milliseconds: 460);

  @override
  Widget build(BuildContext context) {
    final reduceMotion =
        MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    if (reduceMotion) return child;
    final start = (index * 0.14).clamp(0.0, 0.6);
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: _totalDuration,
      curve: Interval(start, 1, curve: Curves.easeOutCubic),
      child: child,
      builder: (context, value, child) {
        return Opacity(
          opacity: value,
          child: Transform.translate(
            offset: Offset(0, 16 * (1 - value)),
            child: child,
          ),
        );
      },
    );
  }
}

/// Wraps [items] so each one cascades in after the previous, continuing the
/// stagger after [startIndex].
List<Widget> onboardingEntranceList(
  List<Widget> items, {
  int startIndex = 0,
}) {
  return <Widget>[
    for (var i = 0; i < items.length; i++)
      OnboardingEntrance(index: startIndex + i, child: items[i]),
  ];
}
