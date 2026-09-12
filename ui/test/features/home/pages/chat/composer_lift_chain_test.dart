import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ui/features/home/pages/chat/chat_page_models.dart';
import 'package:ui/features/home/pages/chat/utils/composer_keyboard_metrics_tracker.dart';
import 'package:ui/features/home/pages/chat/utils/composer_lift_intent_tracker.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'page-level composer chain stays lifted when focus blips during IME open',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(390, 844);
      addTearDown(tester.view.reset);

      final harnessKey = GlobalKey<_ComposerLiftPageHarnessState>();
      await tester.pumpWidget(_ComposerLiftPageHarness(key: harnessKey));
      await tester.pump();

      harnessKey.currentState!.tapComposer();
      await tester.pump();

      harnessKey.currentState!.loseFocus();
      await tester.pump();
      expect(find.text('focus:false lift:true'), findsOneWidget);

      harnessKey.currentState!.setBottomInset(2.33);
      await tester.pump();
      harnessKey.currentState!.setBottomInset(186);
      await tester.pump();
      harnessKey.currentState!.setBottomInset(320);
      await tester.pump();

      expect(
        harnessKey.currentState!.composerDistanceFromScreenBottom,
        greaterThanOrEqualTo(320.0 + kChatKeyboardComposerClearance - 0.01),
      );
      expect(
        harnessKey.currentState!.composerDistanceFromScreenBottom,
        lessThanOrEqualTo(320.0 + kChatKeyboardComposerClearance + 0.01),
      );
      expect(find.text('focus:false lift:true'), findsOneWidget);
    },
  );
}

final _composerKey = UniqueKey();

class _ComposerLiftPageHarness extends StatefulWidget {
  const _ComposerLiftPageHarness({super.key});

  @override
  State<_ComposerLiftPageHarness> createState() =>
      _ComposerLiftPageHarnessState();
}

class _ComposerLiftPageHarnessState extends State<_ComposerLiftPageHarness> {
  static const _viewportSize = Size(390, 844);
  static const _viewPaddingBottom = 24.0;

  final FocusNode _focusNode = FocusNode();
  final ComposerLiftIntentTracker _liftTracker = ComposerLiftIntentTracker();
  final ComposerKeyboardMetricsTracker _metricsTracker =
      ComposerKeyboardMetricsTracker();

  double _bottomInset = 0;
  double _composerDistanceFromScreenBottom = 0;

  double get _safeAreaBottomPadding => _bottomInset >= _viewPaddingBottom
      ? 0
      : _viewPaddingBottom - _bottomInset;
  double get composerDistanceFromScreenBottom =>
      _composerDistanceFromScreenBottom;

  @override
  void initState() {
    super.initState();
    _focusNode.addListener(_handleFocusChanged);
  }

  void tapComposer() {
    _liftTracker.arm();
    _focusNode.requestFocus();
    setState(() {});
  }

  void loseFocus() {
    _focusNode.unfocus();
    setState(() {});
  }

  void setBottomInset(double value) {
    setState(() {
      _bottomInset = value;
    });
  }

  void _handleFocusChanged() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    _focusNode.removeListener(_handleFocusChanged);
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final shouldLift = _liftTracker.update(
      hasInputIntent: _focusNode.hasFocus,
      bottomInset: _bottomInset,
    );
    final metrics = _metricsTracker.update(
      shouldLiftComposerForKeyboard: shouldLift,
      bottomInset: _bottomInset,
      viewPaddingBottom: _viewPaddingBottom,
      safeAreaBottomPadding: _safeAreaBottomPadding,
    );
    final composerBottomOffset =
        metrics.inputBottomPadding + metrics.keyboardSpacer;
    _composerDistanceFromScreenBottom =
        _safeAreaBottomPadding + composerBottomOffset;

    return MaterialApp(
      home: MediaQuery(
        data: MediaQueryData(
          size: _viewportSize,
          viewInsets: EdgeInsets.only(bottom: _bottomInset),
          viewPadding: const EdgeInsets.only(bottom: _viewPaddingBottom),
          padding: EdgeInsets.only(bottom: _safeAreaBottomPadding),
        ),
        child: Scaffold(
          resizeToAvoidBottomInset: false,
          body: SafeArea(
            top: false,
            left: false,
            right: false,
            child: SizedBox.expand(
              child: Stack(
                children: [
                  Positioned(
                    top: 16,
                    left: 16,
                    child: Text(
                      'focus:${_focusNode.hasFocus} lift:$shouldLift',
                    ),
                  ),
                  Positioned(
                    left: 24,
                    right: 24,
                    bottom: composerBottomOffset,
                    child: Focus(
                      focusNode: _focusNode,
                      child: Container(
                        key: _composerKey,
                        height: 56,
                        decoration: BoxDecoration(
                          color: Colors.blueGrey,
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
