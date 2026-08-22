import 'dart:math' as math;

/// Keeps the full-screen chat composer lifted through transient focus loss
/// while the IME is still opening or visibly present.
///
/// Two signals cooperate:
/// - `hasInputIntent` (focus or editing) is the continuous truth: while it is
///   true, the composer should be lifted, full stop. This handles the steady
///   state (focused field with the IME open, focused field after a back-press
///   that hides the IME but keeps focus, focused field tapped to re-show the
///   IME).
/// - `arm()` latches the lift across transient focus loss, e.g. focus blips off
///   for a frame or two while the IME is opening on devices with degraded
///   focus signals. The latch survives `hasInputIntent` going false until the
///   IME is observed clearly closing, has settled closed, or never came up at
///   all within the opening grace window.
class ComposerLiftIntentTracker {
  ComposerLiftIntentTracker({
    this.visibleInsetThreshold = 0.5,
    this.motionEpsilon = 1.0,
    this.openingGraceFrames = 24,
  });

  final double visibleInsetThreshold;
  final double motionEpsilon;
  final int openingGraceFrames;

  bool _latched = false;
  bool _imeVisibleSinceArm = false;
  double? _lastInset;
  int _openingGraceFramesRemaining = 0;

  bool get isLatched => _latched;

  void arm() {
    _latched = true;
    _imeVisibleSinceArm = false;
    _openingGraceFramesRemaining = openingGraceFrames;
  }

  /// Releases chat-owned lift state when focus intentionally moves to an
  /// unrelated text input whose IME may remain visible (for example the home
  /// drawer search field).
  void reset() {
    _latched = false;
    _imeVisibleSinceArm = false;
    _lastInset = null;
    _openingGraceFramesRemaining = 0;
  }

  bool update({required bool hasInputIntent, required double bottomInset}) {
    final inset = bottomInset.isFinite ? math.max(0.0, bottomInset) : 0.0;
    final keyboardVisible = inset > visibleInsetThreshold;

    if (hasInputIntent && !_latched) {
      arm();
    }

    final lastInset = _lastInset;
    if (_latched) {
      if (keyboardVisible) {
        _imeVisibleSinceArm = true;
        _openingGraceFramesRemaining = 0;
      }

      final keyboardClearlyClosing =
          !hasInputIntent &&
          _imeVisibleSinceArm &&
          lastInset != null &&
          inset < lastInset - motionEpsilon;
      final keyboardSettledClosed =
          !hasInputIntent &&
          _imeVisibleSinceArm &&
          inset <= visibleInsetThreshold;
      final openingExpired =
          !hasInputIntent &&
          !_imeVisibleSinceArm &&
          inset <= visibleInsetThreshold &&
          _openingGraceFramesRemaining <= 0;

      if (keyboardClearlyClosing || keyboardSettledClosed || openingExpired) {
        _latched = false;
        _imeVisibleSinceArm = false;
        _openingGraceFramesRemaining = 0;
      } else if (!keyboardVisible &&
          !hasInputIntent &&
          !_imeVisibleSinceArm &&
          _openingGraceFramesRemaining > 0) {
        _openingGraceFramesRemaining -= 1;
      }
    }

    _lastInset = inset;
    return hasInputIntent || _latched;
  }
}
