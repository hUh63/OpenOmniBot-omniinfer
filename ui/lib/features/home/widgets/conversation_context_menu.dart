import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:ui/theme/theme_context.dart';

/// A focused preview and action surface for the selected conversation.
/// The caller retains all action and conversation ownership.
/// Its route transition must also absorb pointers while reversing: ModalRoute
/// ignores its entire page subtree during that phase.
class ConversationContextMenu extends StatelessWidget {
  const ConversationContextMenu({
    super.key,
    required this.anchor,
    required this.title,
    required this.actions,
    required this.animation,
    required this.onDismiss,
  });

  final Rect anchor;
  final String title;
  final List<Widget> actions;
  final Animation<double> animation;
  final VoidCallback onDismiss;

  void _dismiss(BuildContext context) {
    // Check synchronously too: a second pointer can arrive before the first
    // reverse-animation frame rebuilds the AbsorbPointer below.
    if (animation.status == AnimationStatus.reverse ||
        ModalRoute.of(context)?.isCurrent == false) {
      return;
    }
    onDismiss();
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final media = MediaQuery.of(context);
    final isDark = context.isDarkTheme;
    return LayoutBuilder(
      builder: (context, constraints) {
        const margin = 24.0;
        final width = math.max(
          0.0,
          math.min(
            anchor.width,
            math.min(360.0, constraints.maxWidth - margin * 2),
          ),
        );
        final topInset = media.padding.top + 12;
        final bottomInset =
            math.max(media.padding.bottom, media.viewInsets.bottom) + 12;
        final shadow = [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.20 : 0.06),
            blurRadius: 18,
            offset: const Offset(0, 6),
          ),
        ];
        final preview = _buildPreview(context, width, shadow);
        final menu = _buildActions(context, width, shadow);
        return AnimatedBuilder(
          animation: animation,
          builder: (context, _) {
            final progress = media.disableAnimations ? 1.0 : animation.value;
            final lift = const Interval(
              0,
              0.72,
              curve: Curves.easeOutCubic,
            ).transform(progress);
            final reveal = const Interval(
              0.22,
              1,
              curve: Curves.easeOutCubic,
            ).transform(progress);
            final previewOpacity = const Interval(
              0,
              0.25,
              curve: Curves.easeOutCubic,
            ).transform(progress);
            return AbsorbPointer(
              absorbing: animation.status == AnimationStatus.reverse,
              child: Stack(
                children: [
                  Positioned.fill(
                    child: GestureDetector(
                      key: const ValueKey('conversation-context-dismiss'),
                      behavior: HitTestBehavior.opaque,
                      onTap: () => _dismiss(context),
                      child: Semantics(
                        label: MaterialLocalizations.of(context)
                            .modalBarrierDismissLabel,
                        button: true,
                        child: ColoredBox(
                          color: palette.overlayScrim.withValues(
                            alpha:
                                (isDark ? 0.24 : 0.12) *
                                Curves.easeOut.transform(progress),
                          ),
                        ),
                      ),
                    ),
                  ),
                  CustomSingleChildLayout(
                    delegate: _ConversationMenuLayout(
                      anchor: anchor,
                      width: width,
                      topInset: topInset,
                      bottomInset: bottomInset,
                      lift: lift,
                    ),
                    child: Material(
                      type: MaterialType.transparency,
                      child: SingleChildScrollView(
                        key: const ValueKey('conversation-context-scroll'),
                        // Only the painted preview and action surface own taps.
                        // The unused right side of the narrower menu must fall
                        // through to the full-screen dismiss surface.
                        hitTestBehavior: HitTestBehavior.deferToChild,
                        clipBehavior: Clip.hardEdge,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Transform.scale(
                              scale: 0.97 + 0.03 * lift,
                              alignment: Alignment.topLeft,
                              child: Opacity(
                                key: const ValueKey(
                                  'conversation-context-preview-opacity',
                                ),
                                opacity: previewOpacity,
                                child: preview,
                              ),
                            ),
                            const SizedBox(height: 14),
                            IgnorePointer(
                              ignoring: reveal == 0,
                              child: ExcludeSemantics(
                                excluding: reveal < 1,
                                child: ClipRect(
                                  clipper: _ConversationMenuReveal(reveal),
                                  child: Opacity(
                                    key: const ValueKey(
                                      'conversation-context-actions-opacity',
                                    ),
                                    opacity: reveal,
                                    child: Transform.translate(
                                      offset: Offset(0, -8 * (1 - reveal)),
                                      child: menu,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Widget _buildPreview(
    BuildContext context,
    double width,
    List<BoxShadow> shadow,
  ) {
    final palette = context.omniPalette;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () {},
      child: Container(
        key: const ValueKey('conversation-context-preview'),
        width: width,
        constraints: const BoxConstraints(minHeight: 54),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: palette.surfacePrimary,
          borderRadius: BorderRadius.circular(18),
          boxShadow: shadow,
        ),
        child: Row(
          children: [
            Container(
              width: 6,
              height: 6,
              decoration: BoxDecoration(
                color: palette.borderStrong,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: palette.textPrimary,
                  fontSize: 15,
                  height: 1.4,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActions(
    BuildContext context,
    double width,
    List<BoxShadow> shadow,
  ) {
    final palette = context.omniPalette;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () {},
      child: Container(
        key: const ValueKey('conversation-context-actions'),
        width: math.min(width, 250),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: palette.surfacePrimary,
          borderRadius: BorderRadius.circular(20),
          boxShadow: shadow,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: actions,
        ),
      ),
    );
  }
}

class _ConversationMenuReveal extends CustomClipper<Rect> {
  const _ConversationMenuReveal(this.progress);
  final double progress;

  @override
  Rect getClip(Size size) =>
      Rect.fromLTRB(-32, -32, size.width + 32, (size.height + 32) * progress);

  @override
  bool shouldReclip(_ConversationMenuReveal oldClipper) =>
      oldClipper.progress != progress;
}

class _ConversationMenuLayout extends SingleChildLayoutDelegate {
  const _ConversationMenuLayout({
    required this.anchor,
    required this.width,
    required this.topInset,
    required this.bottomInset,
    required this.lift,
  });

  final Rect anchor;
  final double width;
  final double topInset;
  final double bottomInset;
  final double lift;

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) {
    return BoxConstraints(
      minWidth: width,
      maxWidth: width,
      maxHeight: math.max(0, constraints.maxHeight - topInset - bottomInset),
    );
  }

  @override
  Offset getPositionForChild(Size size, Size childSize) {
    final destination = Offset(
      anchor.left.clamp(
        24.0,
        math.max(24.0, size.width - 24 - childSize.width),
      ),
      (anchor.top - 8).clamp(
        topInset,
        math.max(topInset, size.height - bottomInset - childSize.height),
      ),
    );
    return Offset.lerp(anchor.topLeft, destination, lift)!;
  }

  @override
  bool shouldRelayout(_ConversationMenuLayout oldDelegate) =>
      anchor != oldDelegate.anchor ||
      width != oldDelegate.width ||
      topInset != oldDelegate.topInset ||
      bottomInset != oldDelegate.bottomInset ||
      lift != oldDelegate.lift;
}
