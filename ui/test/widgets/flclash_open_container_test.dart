import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/services/storage_service.dart';
import 'package:ui/theme/app_theme.dart';
import 'package:ui/widgets/flclash/open_container.dart';
import 'package:ui/widgets/flclash/drag_back.dart';
import 'package:ui/widgets/predictive_back_route.dart';

void main() {
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
  });

  testWidgets('expands a row without relaying out its page on every frame', (
    tester,
  ) async {
    var layouts = 0;
    final harness = _Harness(
      page: LayoutBuilder(
        builder: (_, constraints) {
          layouts++;
          return const Scaffold(body: Text('Detail', key: ValueKey('detail')));
        },
      ),
    );
    await tester.pumpWidget(harness.app);
    await tester.tap(find.text('Open settings'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 1));
    final afterFirstFrame = layouts;
    final initialTop = tester
        .getTopLeft(find.byKey(const ValueKey('detail')))
        .dy;
    await tester.pump(const Duration(milliseconds: 100));
    final middleTop = tester
        .getTopLeft(find.byKey(const ValueKey('detail')))
        .dy;
    expect(middleTop, lessThan(initialTop));
    await tester.pump(const Duration(milliseconds: 100));
    expect(layouts, afterFirstFrame);
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(find.byKey(const ValueKey('detail'))).dy, 0);
    expect(harness.router.canPop(), isTrue);
    harness.router.pop('saved');
    await tester.pumpAndSettle();
    expect(harness.result, 'saved');
    expect(find.text('Open settings'), findsOneWidget);
    expect(harness.router.canPop(), isFalse);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
    harness.router.dispose();
  });

  testWidgets('repeated opener taps admit only one route', (tester) async {
    final harness = _Harness();
    await tester.pumpWidget(harness.app);
    final tap = tester.widget<InkWell>(find.byType(InkWell)).onTap!;
    tap();
    tap();
    await tester.pumpAndSettle();
    expect(harness.opens, 1);
    harness.router.pop();
    await tester.pumpAndSettle();
    expect(harness.router.canPop(), isFalse);
    await tester.tap(find.text('Open settings'));
    await tester.pumpAndSettle();
    expect(harness.opens, 2);
    await tester.pumpWidget(const SizedBox.shrink());
    harness.router.dispose();
  });

  testWidgets(
    'Tools fade windows and interrupted reverse keep the same geometry',
    (tester) async {
      final harness = _Harness();
      await tester.pumpWidget(harness.app);
      final source = tester.getRect(find.byType(FlClashOpenContainer));
      final full = Offset.zero & tester.getSize(find.byType(MaterialApp));
      await tester.tap(find.text('Open settings'));
      await tester.pump();
      FadeTransition fade() => tester.widget<FadeTransition>(
        find
            .ancestor(
              of: find.text('Detail'),
              matching: find.byType(FadeTransition),
            )
            .first,
      );
      // Values come from Tools' actual OpenContainer: second fifth on entry,
      // fourth fifth on exit. The page translates under the expanding rect.
      await tester.pump(const Duration(milliseconds: 60));
      expect(fade().opacity.value, closeTo(0, 1e-6));
      await tester.pump(const Duration(milliseconds: 30));
      expect(fade().opacity.value, closeTo(0.5, 1e-6));
      final expected = Rect.lerp(
        source,
        full,
        Curves.fastOutSlowIn.transform(0.3),
      )!;
      final beforePop = tester.getTopLeft(find.text('Detail'));
      expect(beforePop.dx, closeTo(expected.left, 0.01));
      expect(beforePop.dy, closeTo(expected.top, 0.01));
      harness.router.pop();
      await tester.pump();
      // A quick close reverses the in-flight curve, not the completed-pop curve.
      expect(tester.getTopLeft(find.text('Detail')), beforePop);
      expect(fade().opacity.value, closeTo(0.5, 1e-6));
      await tester.pumpAndSettle();
      expect(tester.getRect(find.byType(FlClashOpenContainer)), source);
      await tester.tap(find.text('Open settings'));
      await tester.pumpAndSettle();
      harness.router.pop();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      expect(fade().opacity.value, closeTo(1, 1e-6));
      await tester.pump(const Duration(milliseconds: 30));
      expect(fade().opacity.value, closeTo(0.5, 1e-6));
      await tester.pump(const Duration(milliseconds: 30));
      expect(fade().opacity.value, closeTo(0, 1e-6));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      harness.router.dispose();
    },
  );

  testWidgets(
    'a nested source measures in the destination navigator on both legs',
    (tester) async {
      final harness = _Harness(
        sourceHost: (child) => Padding(
          padding: const EdgeInsets.only(left: 35, top: 75),
          child: Navigator(
            onGenerateRoute: (_) =>
                MaterialPageRoute<void>(builder: (_) => Scaffold(body: child)),
          ),
        ),
      );
      await tester.pumpWidget(harness.app);
      await tester.pumpAndSettle();
      final source = tester.getRect(find.byType(FlClashOpenContainer));
      final full = Offset.zero & tester.getSize(find.byType(MaterialApp));
      await tester.tap(find.text('Open settings'));
      await tester.pump();
      await tester.pump(); // Finish Navigator's offstage preparation at t=0.
      expect(tester.getTopLeft(find.text('Detail')), source.topLeft);
      await tester.pump(const Duration(milliseconds: 150));
      final opening = Rect.lerp(
        source,
        full,
        Curves.fastOutSlowIn.transform(0.5),
      )!;
      expect(
        tester.getTopLeft(find.text('Detail')).dx,
        closeTo(opening.left, 0.01),
      );
      expect(
        tester.getTopLeft(find.text('Detail')).dy,
        closeTo(opening.top, 0.01),
      );
      await tester.pumpAndSettle();
      harness.router.pop();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 150));
      final closing = Rect.lerp(
        source,
        full,
        Curves.fastOutSlowIn.flipped.transform(0.5),
      )!;
      expect(
        tester.getTopLeft(find.text('Detail')).dx,
        closeTo(closing.left, 0.01),
      );
      expect(
        tester.getTopLeft(find.text('Detail')).dy,
        closeTo(closing.top, 0.01),
      );
      await tester.pumpAndSettle();
      expect(tester.getRect(find.byType(FlClashOpenContainer)), source);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      harness.router.dispose();
    },
  );

  testWidgets('resized destination returns to the existing opener', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(390, 844);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    final harness = _Harness();
    await tester.pumpWidget(harness.app);
    await tester.tap(find.text('Open settings'));
    await tester.pumpAndSettle();
    tester.view.physicalSize = const Size(844, 390);
    await tester.pumpAndSettle();
    harness.router.pop();
    await tester.pumpAndSettle();
    expect(find.text('Open settings'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
    harness.router.dispose();
  });

  for (final edge in SwipeEdge.values) {
    testWidgets('Android ${edge.name} edge commits the Tools row contraction', (
      tester,
    ) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      try {
        final harness = _Harness();
        await tester.pumpWidget(harness.app);
        final sourceRect = tester.getRect(find.byType(FlClashOpenContainer));
        await tester.tap(find.text('Open settings'));
        await tester.pumpAndSettle();
        final context = tester.element(find.text('Detail'));
        final route = ModalRoute.of(context)! as PageRoute<dynamic>;
        expect(route, isA<DragBackRouteMixin<dynamic>>());
        expect(route, isNot(isA<PredictiveBackRouteMotion<dynamic>>()));
        final navigator = route.navigator!;
        expect(route.popGestureEnabled, isTrue);
        await _sendBackGesture(tester, 'startBackGesture', _backEvent(edge, 0));
        await _sendBackGesture(
          tester,
          'updateBackGestureProgress',
          _backEvent(edge, 0.35),
        );
        await tester.pump();
        // FlClash has a pointer drag detector, not a system-back observer.
        // An Android edge gesture must not switch _settled to the slide path.
        expect((route as DragBackRouteMixin).isDragBackActive, isFalse);
        expect(tester.getTopLeft(find.text('Detail')), Offset.zero);
        expect(navigator.userGestureInProgress, isFalse);
        await _sendBackGesture(tester, 'cancelBackGesture');
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 175));
        expect(route.animation!.value, 1);
        await tester.pumpAndSettle();
        expect(harness.router.canPop(), isTrue);
        expect(navigator.userGestureInProgress, isFalse);
        expect(tester.getTopLeft(find.text('Detail')).dx, 0);
        await _sendBackGesture(tester, 'startBackGesture', _backEvent(edge, 0));
        // The system's commit now follows the same ordinary pop as Tools.
        await _sendBackGesture(
          tester,
          'updateBackGestureProgress',
          _backEvent(edge, 0.25),
        );
        await tester.pump();
        await _sendBackGesture(tester, 'commitBackGesture');
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 150));
        expect(route.animation!.value, closeTo(0.5, 1e-5));
        expect((route as DragBackRouteMixin).isDragBackActive, isFalse);
        final full = Offset.zero & tester.getSize(find.byType(MaterialApp));
        final expected = Rect.lerp(
          sourceRect,
          full,
          Curves.fastOutSlowIn.flipped.transform(0.5),
        )!;
        final position = tester.getTopLeft(find.text('Detail'));
        expect(position.dx, closeTo(expected.left, 0.01));
        expect(position.dy, closeTo(expected.top, 0.01));
        await tester.pumpAndSettle();
        expect(harness.router.canPop(), isFalse);
        expect(navigator.userGestureInProgress, isFalse);
        expect(find.text('Open settings'), findsOneWidget);
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
        harness.router.dispose();
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
    });
  }

  testWidgets(
    'pointer drag follows the finger and uses upstream distance settle',
    (tester) async {
      final harness = _Harness();
      await tester.pumpWidget(harness.app);
      await tester.tap(find.text('Open settings'));
      await tester.pumpAndSettle();
      final route = ModalRoute.of(tester.element(find.text('Detail')))!;
      final navigator = route.navigator!;
      final gesture = await tester.startGesture(const Offset(50, 300));
      await gesture.moveBy(const Offset(50, 0));
      await gesture.moveBy(const Offset(100, 0));
      await tester.pump();
      final firstPosition = tester.getTopLeft(find.text('Detail')).dx;
      await gesture.moveBy(const Offset(80, 0));
      await tester.pump();
      expect(
        tester.getTopLeft(find.text('Detail')).dx - firstPosition,
        closeTo(80, 0.01),
      );
      await tester.pump(const Duration(milliseconds: 600));
      await gesture.up();
      await tester.pumpAndSettle();
      expect(harness.router.canPop(), isTrue);
      expect(navigator.userGestureInProgress, isFalse);

      final commit = await tester.startGesture(const Offset(50, 300));
      await commit.moveBy(const Offset(50, 0));
      await commit.moveBy(const Offset(500, 0));
      await tester.pump(const Duration(milliseconds: 600));
      await commit.up();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 349));
      expect(route.animation!.isAnimating, isTrue);
      await tester.pump(const Duration(milliseconds: 1));
      await tester.pumpAndSettle();
      expect(harness.router.canPop(), isFalse);
      expect(navigator.userGestureInProgress, isFalse);
      expect(find.text('Open settings'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      harness.router.dispose();
    },
  );

  testWidgets('system back button contracts to its row without a drag slide', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    try {
      final harness = _Harness();
      await tester.pumpWidget(harness.app);
      await tester.tap(find.text('Open settings'));
      await tester.pumpAndSettle();
      final route = ModalRoute.of(tester.element(find.text('Detail')))!;
      await _sendBackGesture(tester, 'startBackGesture', {
        'touchOffset': null,
        'progress': 0.0,
        'swipeEdge': 0,
      });
      await _sendBackGesture(tester, 'commitBackGesture');
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 150));
      expect((route as DragBackRouteMixin).isDragBackActive, isFalse);
      expect(route.animation!.isAnimating, isTrue);
      expect(tester.getTopLeft(find.text('Detail')).dy, greaterThan(0));
      await tester.pumpAndSettle();
      expect(harness.router.canPop(), isFalse);
      expect(find.text('Open settings'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      harness.router.dispose();
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });

  testWidgets(
    'reduced motion skips the container transform and restores the row',
    (tester) async {
      final harness = _Harness(disableAnimations: true);
      await tester.pumpWidget(harness.app);
      await tester.tap(find.text('Open settings'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 1));
      final route = ModalRoute.of(tester.element(find.text('Detail')))!;
      expect(route.transitionDuration, Duration.zero);
      expect(route.animation!.isCompleted, isTrue);
      harness.router.pop();
      await tester.pumpAndSettle();
      expect(find.text('Open settings'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      harness.router.dispose();
    },
  );

  testWidgets('interrupted opening and route replacement restore the source', (
    tester,
  ) async {
    final harness = _Harness();
    await tester.pumpWidget(harness.app);
    await tester.tap(find.text('Open settings'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 40));
    harness.router.pop();
    await tester.pumpAndSettle();
    expect(find.text('Open settings'), findsOneWidget);
    await tester.tap(find.text('Open settings'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 40));
    harness.router.go('/replacement');
    await tester.pumpAndSettle();
    expect(find.text('Replacement'), findsOneWidget);
    harness.router.go('/');
    await tester.pumpAndSettle();
    await tester.tap(find.text('Open settings'));
    await tester.pumpAndSettle();
    expect(find.text('Detail'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
    harness.router.dispose();
  });

  testWidgets('inactive or removed source rows do not retain a hidden opener', (
    tester,
  ) async {
    final visibility = ValueNotifier(0);
    final harness = _Harness(
      sourceHost: (child) => ValueListenableBuilder<int>(
        valueListenable: visibility,
        child: child,
        builder: (_, value, child) => value == 2
            ? const SizedBox.shrink()
            : Offstage(offstage: value == 1, child: child),
      ),
    );
    await tester.pumpWidget(harness.app);
    for (final visibilityWhileCovered in [1, 2]) {
      await tester.tap(find.text('Open settings'));
      await tester.pumpAndSettle();
      visibility.value = visibilityWhileCovered;
      await tester.pump();
      harness.router.pop();
      await tester.pumpAndSettle();
      visibility.value = 0;
      await tester.pumpAndSettle();
      expect(find.text('Open settings'), findsOneWidget);
      expect(tester.takeException(), isNull);
    }
    await tester.pumpWidget(const SizedBox.shrink());
    harness.router.dispose();
    visibility.dispose();
  });

  testWidgets('disabling predictive back retains the programmatic container', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    try {
      await StorageService.setPredictiveBackEnabled(false);
      final harness = _Harness();
      await tester.pumpWidget(harness.app);
      await tester.tap(find.text('Open settings'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      final route = ModalRoute.of(tester.element(find.text('Detail')))!;
      expect(route.transitionDuration, const Duration(milliseconds: 300));
      expect(route.animation!.isAnimating, isTrue);
      await tester.pumpAndSettle();
      expect(route.popGestureEnabled, isFalse);
      await _sendBackGesture(
        tester,
        'startBackGesture',
        _backEvent(SwipeEdge.left, 0),
      );
      await _sendBackGesture(
        tester,
        'updateBackGestureProgress',
        _backEvent(SwipeEdge.left, 0.5),
      );
      await tester.pump();
      expect((route as DragBackRouteMixin).isDragBackActive, isFalse);
      expect(tester.getTopLeft(find.text('Detail')), Offset.zero);
      await _sendBackGesture(tester, 'cancelBackGesture');
      harness.router.pop();
      await tester.pumpAndSettle();
      expect(find.text('Open settings'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      harness.router.dispose();
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });

  testWidgets('a departing nested route blocks the newly current container', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    try {
      final harness = _Harness();
      await tester.pumpWidget(harness.app);
      await tester.tap(find.text('Open settings'));
      await tester.pumpAndSettle();
      final route = ModalRoute.of(tester.element(find.text('Detail')))!;
      final nested = MaterialPageRoute<void>(
        builder: (_) => const Scaffold(body: Text('Nested')),
      );
      route.navigator!.push(nested);
      await tester.pumpAndSettle();
      route.navigator!.pop();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      expect(nested.animation!.isAnimating, isTrue);
      expect(route.isCurrent, isTrue);
      expect(route.secondaryAnimation!.isDismissed, isTrue);
      expect(route.popGestureEnabled, isFalse);
      final blocked = await tester.startGesture(const Offset(50, 300));
      await blocked.moveBy(const Offset(50, 0));
      await blocked.moveBy(const Offset(150, 0));
      await tester.pump();
      expect((route as DragBackRouteMixin).isDragBackActive, isFalse);
      await blocked.cancel();
      await tester.pumpAndSettle();

      // The guard ends with the real overlay animation, not a timer or a stale
      // flag. The same page immediately accepts the next ordinary back gesture.
      expect(route.popGestureEnabled, isTrue);
      final accepted = await tester.startGesture(const Offset(50, 300));
      await accepted.moveBy(const Offset(50, 0));
      await accepted.moveBy(const Offset(150, 0));
      await tester.pump();
      expect(route.isDragBackActive, isTrue);
      await accepted.cancel();
      await tester.pumpAndSettle();
      expect(harness.router.canPop(), isTrue);
      expect(route.navigator!.userGestureInProgress, isFalse);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      harness.router.dispose();
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });

  testWidgets(
    'PopScope remains the owner of whether container back is allowed',
    (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      final canPop = ValueNotifier(false);
      try {
        final harness = _Harness(
          page: ValueListenableBuilder<bool>(
            valueListenable: canPop,
            builder: (_, allowed, _) => PopScope(
              canPop: allowed,
              child: const Scaffold(body: Text('Detail')),
            ),
          ),
        );
        await tester.pumpWidget(harness.app);
        await tester.tap(find.text('Open settings'));
        await tester.pumpAndSettle();
        final route = ModalRoute.of(tester.element(find.text('Detail')))!;
        expect(route.popGestureEnabled, isFalse);
        await _sendBackGesture(
          tester,
          'startBackGesture',
          _backEvent(SwipeEdge.right, 0),
        );
        await _sendBackGesture(
          tester,
          'updateBackGestureProgress',
          _backEvent(SwipeEdge.right, 0.6),
        );
        await tester.pump();
        expect((route as DragBackRouteMixin).isDragBackActive, isFalse);
        await _sendBackGesture(tester, 'commitBackGesture');
        await tester.pumpAndSettle();
        expect(harness.router.canPop(), isTrue);
        canPop.value = true;
        await tester.pumpAndSettle();
        expect(route.popGestureEnabled, isTrue);
        await _sendBackGesture(
          tester,
          'startBackGesture',
          _backEvent(SwipeEdge.right, 0),
        );
        await _sendBackGesture(
          tester,
          'updateBackGestureProgress',
          _backEvent(SwipeEdge.right, 0.6),
        );
        await _sendBackGesture(tester, 'commitBackGesture');
        await tester.pumpAndSettle();
        expect(harness.router.canPop(), isFalse);
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
        harness.router.dispose();
      } finally {
        canPop.dispose();
        debugDefaultTargetPlatformOverride = null;
      }
    },
  );
}

Map<String, dynamic> _backEvent(SwipeEdge edge, double progress) => {
  'touchOffset': <double>[edge == SwipeEdge.left ? 1 : 799, 300],
  'progress': progress,
  'swipeEdge': edge.index,
};

Future<void> _sendBackGesture(
  WidgetTester tester,
  String method, [
  Map<String, dynamic>? arguments,
]) => tester.binding.defaultBinaryMessenger.handlePlatformMessage(
  'flutter/backgesture',
  const StandardMethodCodec().encodeMethodCall(MethodCall(method, arguments)),
  (_) {},
);

class _Harness {
  _Harness({
    Widget? page,
    this.disableAnimations = false,
    Widget Function(Widget)? sourceHost,
  }) {
    router = GoRouter(
      routes: [
        GoRoute(
          path: '/',
          builder: (_, _) => Scaffold(
            body: (sourceHost ?? (child) => child)(
              Padding(
                padding: const EdgeInsets.fromLTRB(22, 180, 22, 0),
                child: FlClashOpenContainer(
                  onOpen: (source) {
                    opens++;
                    return router.push<Object?>('/detail', extra: source);
                  },
                  onClosed: (value) => result = value,
                  closedBuilder: (_, open) => InkWell(
                    onTap: open,
                    child: const SizedBox(
                      width: double.infinity,
                      height: 60,
                      child: Text('Open settings'),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
        GoRoute(
          path: '/detail',
          pageBuilder: (_, state) =>
              (state.extra! as FlClashContainerSource).createPage<Object?>(
                key: state.pageKey,
                child: page ?? const Scaffold(body: Text('Detail')),
              ),
        ),
        GoRoute(
          path: '/replacement',
          builder: (_, _) => const Scaffold(body: Text('Replacement')),
        ),
      ],
    );
  }

  late final GoRouter router;
  final bool disableAnimations;
  int opens = 0;
  Object? result;

  Widget get app => MaterialApp.router(
    theme: AppTheme.lightTheme,
    routerConfig: router,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context)
          .copyWith(disableAnimations: disableAnimations),
      child: child!,
    ),
  );
}
