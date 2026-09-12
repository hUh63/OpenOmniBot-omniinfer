import 'package:flutter/material.dart';

/// Unified popup-menu anchoring that prefers showing below the trigger,
/// and falls back to above when there is insufficient room.
class PopupMenuAnchorPosition {
  static const double _defaultSafePadding = 8;
  static const double _defaultVerticalGap = 4;

  static RelativeRect fromAnchorRect({
    required Rect anchorRect,
    required Size overlaySize,
    double estimatedMenuHeight = 260,
    double safePadding = _defaultSafePadding,
    double verticalGap = _defaultVerticalGap,
    bool preferBelow = true,
    double reservedBottom = 0,
  }) {
    final usableTop = safePadding;
    final maxUsableBottom = overlaySize.height - safePadding;
    final rawUsableBottom = overlaySize.height - safePadding - reservedBottom;
    final usableBottom = rawUsableBottom < usableTop
        ? usableTop
        : (rawUsableBottom > maxUsableBottom
              ? maxUsableBottom
              : rawUsableBottom);
    final usableLeft = safePadding;
    final rawUsableRight = overlaySize.width - safePadding;
    final usableRight = rawUsableRight < usableLeft
        ? usableLeft
        : (rawUsableRight > overlaySize.width
              ? overlaySize.width
              : rawUsableRight);

    final anchorTop = anchorRect.top.clamp(usableTop, usableBottom).toDouble();
    final anchorBottom = anchorRect.bottom
        .clamp(usableTop, usableBottom)
        .toDouble();
    final anchorLeft = anchorRect.left.clamp(usableLeft, usableRight).toDouble();

    final spaceBelow = (usableBottom - anchorBottom).toDouble();
    final spaceAbove = (anchorTop - usableTop).toDouble();
    final placeBelow = preferBelow
        ? (spaceBelow >= estimatedMenuHeight || spaceBelow >= spaceAbove)
        : !(spaceAbove >= estimatedMenuHeight || spaceAbove >= spaceBelow);

    final targetY = placeBelow
        ? (anchorBottom + verticalGap).clamp(usableTop, usableBottom)
        : (anchorTop - verticalGap).clamp(usableTop, usableBottom);

    final pointRect = Rect.fromLTWH(anchorLeft, targetY.toDouble(), 0, 0);
    return RelativeRect.fromRect(pointRect, Offset.zero & overlaySize);
  }

  static RelativeRect fromOverlayOffset({
    required Offset overlayOffset,
    required Size overlaySize,
    double estimatedMenuHeight = 220,
    double safePadding = _defaultSafePadding,
    double verticalGap = _defaultVerticalGap,
    bool preferBelow = true,
    double reservedBottom = 0,
  }) {
    final anchorRect = Rect.fromLTWH(overlayOffset.dx, overlayOffset.dy, 0, 0);
    return fromAnchorRect(
      anchorRect: anchorRect,
      overlaySize: overlaySize,
      estimatedMenuHeight: estimatedMenuHeight,
      safePadding: safePadding,
      verticalGap: verticalGap,
      preferBelow: preferBelow,
      reservedBottom: reservedBottom,
    );
  }

  static RelativeRect fromGlobalOffset({
    required BuildContext context,
    required Offset globalOffset,
    double estimatedMenuHeight = 220,
    double safePadding = _defaultSafePadding,
    double verticalGap = _defaultVerticalGap,
    bool preferBelow = true,
    double reservedBottom = 0,
  }) {
    final overlay = Overlay.of(context).context.findRenderObject() as RenderBox?;
    if (overlay == null) {
      return RelativeRect.fromLTRB(
        globalOffset.dx,
        globalOffset.dy,
        globalOffset.dx + 1,
        globalOffset.dy + 1,
      );
    }
    final overlayOffset = overlay.globalToLocal(globalOffset);
    return fromOverlayOffset(
      overlayOffset: overlayOffset,
      overlaySize: overlay.size,
      estimatedMenuHeight: estimatedMenuHeight,
      safePadding: safePadding,
      verticalGap: verticalGap,
      preferBelow: preferBelow,
      reservedBottom: reservedBottom,
    );
  }
}
