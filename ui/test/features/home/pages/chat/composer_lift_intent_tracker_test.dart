import 'package:flutter_test/flutter_test.dart';
import 'package:ui/features/home/pages/chat/utils/composer_lift_intent_tracker.dart';

void main() {
  test('keeps lift latched through transient focus loss while IME opens', () {
    final tracker = ComposerLiftIntentTracker();

    tracker.arm();

    expect(tracker.update(hasInputIntent: false, bottomInset: 0), isTrue);
    expect(tracker.update(hasInputIntent: false, bottomInset: 186), isTrue);
    expect(tracker.update(hasInputIntent: false, bottomInset: 301), isTrue);
  });

  test(
    'keeps lift latched while IME remains visibly stable after focus loss',
    () {
      final tracker = ComposerLiftIntentTracker();

      tracker.arm();

      expect(tracker.update(hasInputIntent: false, bottomInset: 312), isTrue);
      expect(tracker.update(hasInputIntent: false, bottomInset: 312), isTrue);
      expect(tracker.update(hasInputIntent: false, bottomInset: 311.4), isTrue);
    },
  );

  test('releases latch once keyboard clearly starts closing', () {
    final tracker = ComposerLiftIntentTracker();

    tracker.arm();

    expect(tracker.update(hasInputIntent: false, bottomInset: 320), isTrue);
    expect(tracker.update(hasInputIntent: false, bottomInset: 320), isTrue);
    expect(tracker.update(hasInputIntent: false, bottomInset: 304), isFalse);
  });

  test('does not lift a visible keyboard without prior input intent', () {
    final tracker = ComposerLiftIntentTracker();

    expect(tracker.update(hasInputIntent: false, bottomInset: 288), isFalse);
  });

  test('reset prevents a foreign IME from keeping the composer lifted', () {
    final tracker = ComposerLiftIntentTracker();

    expect(tracker.update(hasInputIntent: true, bottomInset: 320), isTrue);

    tracker.reset();

    expect(tracker.update(hasInputIntent: false, bottomInset: 320), isFalse);
    expect(tracker.update(hasInputIntent: false, bottomInset: 320), isFalse);
  });

  test('clears the latch after the keyboard settles closed', () {
    final tracker = ComposerLiftIntentTracker();

    tracker.arm();

    expect(tracker.update(hasInputIntent: false, bottomInset: 260), isTrue);
    expect(tracker.update(hasInputIntent: false, bottomInset: 0), isFalse);
    expect(tracker.update(hasInputIntent: false, bottomInset: 280), isFalse);
  });

  test('releases a pending arm after the keyboard never becomes visible', () {
    final tracker = ComposerLiftIntentTracker(openingGraceFrames: 2);

    tracker.arm();

    expect(tracker.update(hasInputIntent: false, bottomInset: 0), isTrue);
    expect(tracker.update(hasInputIntent: false, bottomInset: 0), isTrue);
    expect(tracker.update(hasInputIntent: false, bottomInset: 0), isFalse);
  });

  test('input intent keeps lift armed without an explicit arm', () {
    final tracker = ComposerLiftIntentTracker();

    expect(tracker.update(hasInputIntent: true, bottomInset: 0), isTrue);
    expect(tracker.update(hasInputIntent: true, bottomInset: 320), isTrue);
    expect(tracker.update(hasInputIntent: false, bottomInset: 320), isTrue);
  });

  test(
    'stays lifted while focus is retained even if the IME is slow to appear',
    () {
      // Regression: on slow IMEs the opening grace can expire before the
      // keyboard reports a non-zero inset. Focus alone must keep the lift on.
      final tracker = ComposerLiftIntentTracker(openingGraceFrames: 2);

      for (var i = 0; i < 10; i++) {
        expect(tracker.update(hasInputIntent: true, bottomInset: 0), isTrue);
      }
      expect(tracker.update(hasInputIntent: true, bottomInset: 186), isTrue);
      expect(tracker.update(hasInputIntent: true, bottomInset: 320), isTrue);
    },
  );

  test(
    'reopening the IME while focus is retained keeps the composer lifted',
    () {
      // Regression: text present, user closes IME via back, then taps the
      // already-focused field to reopen it.
      final tracker = ComposerLiftIntentTracker();

      tracker.arm();
      expect(tracker.update(hasInputIntent: true, bottomInset: 0), isTrue);
      expect(tracker.update(hasInputIntent: true, bottomInset: 320), isTrue);
      expect(tracker.update(hasInputIntent: true, bottomInset: 320), isTrue);

      // Back-press closes the IME but keeps focus.
      expect(tracker.update(hasInputIntent: true, bottomInset: 240), isTrue);
      expect(tracker.update(hasInputIntent: true, bottomInset: 0), isTrue);

      // Tap to reopen. arm() may or may not fire (depends on tap path); the
      // continuous focus signal must carry the lift across the open animation.
      expect(tracker.update(hasInputIntent: true, bottomInset: 0), isTrue);
      expect(tracker.update(hasInputIntent: true, bottomInset: 60), isTrue);
      expect(tracker.update(hasInputIntent: true, bottomInset: 320), isTrue);
    },
  );
}
