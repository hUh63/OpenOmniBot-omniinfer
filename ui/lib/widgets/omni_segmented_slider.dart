import 'package:flutter/material.dart';
import 'package:ui/theme/theme_context.dart';

@immutable
class OmniSegmentedOption<T> {
  const OmniSegmentedOption({
    required this.value,
    required this.label,
    this.icon,
    this.id,
  });

  final T value;
  final String label;
  final IconData? icon;
  final String? id;
}

class OmniSegmentedSlider<T> extends StatelessWidget {
  const OmniSegmentedSlider({
    super.key,
    required this.value,
    required this.options,
    required this.onChanged,
    this.position,
    this.keyPrefix = 'omni-segment-option',
    this.height = 44,
  });

  final T value;
  final List<OmniSegmentedOption<T>> options;
  final ValueChanged<T> onChanged;
  final double? position;
  final String keyPrefix;
  final double height;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final isDark = context.isDarkTheme;
    const outerPadding = 3.0;
    final selectedIndex = options.indexWhere((option) => option.value == value);
    final safeIndex = selectedIndex >= 0 ? selectedIndex : 0;
    final visualPosition = (position ?? safeIndex.toDouble()).clamp(
      0.0,
      options.length - 1.0,
    );
    final highlightedIndex = visualPosition.round();

    return Container(
      height: height,
      decoration: BoxDecoration(
        color: palette.segmentTrack,
        borderRadius: BorderRadius.circular(height / 2),
        boxShadow: [
          BoxShadow(
            color: palette.shadowColor.withValues(alpha: isDark ? 0.12 : 0.06),
            blurRadius: 14,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(height / 2),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final thumbWidth =
                (constraints.maxWidth - outerPadding * 2) / options.length;
            return Stack(
              children: [
                Positioned(
                  left: outerPadding + thumbWidth * visualPosition,
                  top: outerPadding,
                  bottom: outerPadding,
                  width: thumbWidth,
                  child: IgnorePointer(
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: palette.segmentThumb,
                        borderRadius: BorderRadius.circular(height / 2),
                        boxShadow: [
                          BoxShadow(
                            color: palette.shadowColor.withValues(
                              alpha: isDark ? 0.20 : 0.10,
                            ),
                            blurRadius: 10,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                Row(
                  children: options
                      .map((option) {
                        final selected =
                            options.indexOf(option) == highlightedIndex;
                        final foreground = selected
                            ? palette.textPrimary
                            : palette.textSecondary;
                        final label = option.icon == null
                            ? Text(option.label)
                            : Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  AnimatedContainer(
                                    duration: const Duration(milliseconds: 180),
                                    curve: Curves.easeOutCubic,
                                    padding: const EdgeInsets.all(3),
                                    decoration: BoxDecoration(
                                      color: selected
                                          ? palette.accentPrimary.withValues(
                                              alpha: isDark ? 0.18 : 0.10,
                                            )
                                          : Colors.transparent,
                                      borderRadius: BorderRadius.circular(999),
                                    ),
                                    child: Icon(
                                      option.icon,
                                      size: 14,
                                      color: selected
                                          ? palette.accentPrimary
                                          : palette.textTertiary,
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  Text(option.label),
                                ],
                              );

                        return Expanded(
                          child: Material(
                            color: Colors.transparent,
                            child: InkWell(
                              key: ValueKey(
                                '$keyPrefix-${option.id ?? option.value.toString()}',
                              ),
                              borderRadius: BorderRadius.circular(height / 2),
                              onTap: () => onChanged(option.value),
                              child: Center(
                                child: AnimatedScale(
                                  duration: const Duration(milliseconds: 180),
                                  curve: Curves.easeOutCubic,
                                  scale: selected ? 1.0 : 0.97,
                                  child: AnimatedDefaultTextStyle(
                                    duration: const Duration(milliseconds: 180),
                                    curve: Curves.easeOutCubic,
                                    style: TextStyle(
                                      fontSize: 13,
                                      fontWeight: selected
                                          ? FontWeight.w700
                                          : FontWeight.w600,
                                      color: foreground,
                                      letterSpacing: selected ? 0.1 : 0,
                                    ),
                                    child: label,
                                  ),
                                ),
                              ),
                            ),
                          ),
                        );
                      })
                      .toList(growable: false),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
