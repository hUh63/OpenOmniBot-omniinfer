import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/services/storage_service.dart';
import 'package:ui/widgets/predictive_back_gesture_wrapper.dart';

/// 使用 PredictiveBackGestureWrapper 作为转场的测试路由,
/// 结构与 go_router_manager 中的 _buildPage(Fade 回退分支)一致。
class _WrapperRoute extends PageRouteBuilder<String> {
  _WrapperRoute({required this.onRouteReady})
    : super(
        transitionDuration: const Duration(milliseconds: 250),
        reverseTransitionDuration: const Duration(milliseconds: 250),
        pageBuilder: (context, animation, secondaryAnimation) {
          return Builder(
            builder: (context) {
              onRouteReady(ModalRoute.of(context));
              return const Scaffold(body: Text('second'));
            },
          );
        },
        transitionsBuilder: (context, animation, secondaryAnimation, child) {
          return PredictiveBackGestureWrapper(
            animation: animation,
            secondaryAnimation: secondaryAnimation,
            transitionBuilder:
                (context, animation, secondaryAnimation, child) =>
                    FadeTransition(opacity: animation, child: child),
            child: child,
          );
        },
      );

  final ValueChanged<ModalRoute<dynamic>?> onRouteReady;
}

/// 经 flutter/backgesture 平台通道模拟引擎侧手势事件
/// (与框架 SDK 测试 predictive_back_page_transitions_builder_test.dart 同款手法)。
Future<void> _sendBackGesture(
  WidgetTester tester,
  String method, [
  Map<String, dynamic>? arguments,
]) async {
  final ByteData message = const StandardMethodCodec().encodeMethodCall(
    MethodCall(method, arguments),
  );
  await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
    'flutter/backgesture',
    message,
    (ByteData? _) {},
  );
}

Future<void> _startBackGesture(WidgetTester tester, double progress) {
  return _sendBackGesture(tester, 'startBackGesture', <String, dynamic>{
    'touchOffset': <double>[0.0, 300.0],
    'progress': progress,
    'swipeEdge': 0, // left
  });
}

Future<void> _updateBackGesture(WidgetTester tester, double progress) {
  return _sendBackGesture(
    tester,
    'updateBackGestureProgress',
    <String, dynamic>{
      'touchOffset': <double>[100.0, 300.0],
      'progress': progress,
      'swipeEdge': 0, // left
    },
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    await StorageService.init();
  });

  /// 自举应用并 push 出带 wrapper 的二级页面,返回捕获到的路由。
  Future<ModalRoute<dynamic>?> bootstrap(WidgetTester tester) async {
    ModalRoute<dynamic>? route;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) {
              return TextButton(
                onPressed: () {
                  Navigator.of(
                    context,
                  ).push(_WrapperRoute(onRouteReady: (r) => route = r));
                },
                child: const Text('push'),
              );
            },
          ),
        ),
      ),
    );
    await tester.tap(find.text('push'));
    await tester.pumpAndSettle();
    expect(find.text('second'), findsOneWidget);
    return route;
  }

  Finder clipFinder() =>
      find.ancestor(of: find.text('second'), matching: find.byType(ClipRRect));

  testWidgets(
    'gesture drives route controller, slide transition and corner clip; '
    'cancel restores the page',
    (tester) async {
      final route = await bootstrap(tester);
      expect(route, isNotNull);

      // 手势开始:消费事件,进入 popGestureInProgress,
      // 顶层页出现圆角且转场切换为线性(跟手)。
      await _startBackGesture(tester, 0.0);
      await tester.pump();
      expect(route!.popGestureInProgress, isTrue);
      final clip = tester.widget<ClipRRect>(clipFinder());
      expect(clip.borderRadius, BorderRadius.circular(32.0));
      expect(clip.clipBehavior, Clip.hardEdge);
      final slide = tester.widget<CupertinoPageTransition>(
        find.ancestor(
          of: find.text('second'),
          matching: find.byType(CupertinoPageTransition),
        ),
      );
      expect(slide.linearTransition, isTrue);

      // 进度 0.5:控制器被取反驱动为 0.5。
      await _updateBackGesture(tester, 0.5);
      await tester.pump();
      expect(route.animation!.value, closeTo(0.5, 0.001));

      // 取消:页面弹回,路由保留,动画回到 1,圆角消失。
      await _sendBackGesture(tester, 'cancelBackGesture');
      await tester.pumpAndSettle();
      expect(find.text('second'), findsOneWidget);
      expect(route.popGestureInProgress, isFalse);
      expect(route.animation!.value, closeTo(1.0, 0.001));
      expect(
        tester.widget<ClipRRect>(clipFinder()).borderRadius,
        BorderRadius.zero,
      );
      expect(tester.widget<ClipRRect>(clipFinder()).clipBehavior, Clip.none);
    },
    // wrapper 仅在 Android 消费手势;variant 的 tearDown 会在测试框架
    // 校验 debug 变量之前复位 debugDefaultTargetPlatformOverride。
    variant: TargetPlatformVariant.only(TargetPlatform.android),
  );

  testWidgets(
    'commit settles forward from the drag position without bouncing back',
    (tester) async {
      final route = await bootstrap(tester);

      await _startBackGesture(tester, 0.0);
      await tester.pump();
      await _updateBackGesture(tester, 0.8);
      await tester.pump();
      expect(route!.animation!.value, closeTo(0.2, 0.001));

      await _sendBackGesture(tester, 'commitBackGesture');
      // 收尾期间控制器只能从松手位置(0.2)向 0 前进,不得向 1.0 回跳
      // (TransitionRoute._handleDragEnd 的 reverse(from: 1.0) 重播路径)。
      var previous = 0.2;
      for (var i = 0; i < 10 && route.isCurrent; i++) {
        await tester.pump(const Duration(milliseconds: 30));
        final value = route.animation?.value;
        if (value == null) {
          break;
        }
        expect(value, lessThanOrEqualTo(previous + 0.001));
        previous = value;
      }
      await tester.pumpAndSettle();

      expect(find.text('second'), findsNothing);
      expect(route.isCurrent, isFalse);
    },
    variant: TargetPlatformVariant.only(TargetPlatform.android),
  );

  testWidgets(
    'toggle off: gesture is not consumed, legacy transition, plain pop',
    (tester) async {
      await StorageService.setPredictiveBackEnabled(false);
      final route = await bootstrap(tester);

      await _startBackGesture(tester, 0.0);
      await tester.pump();
      // 未消费:无手势状态;回退 Fade 转场,wrapper 不挂 ClipRRect。
      expect(route!.popGestureInProgress, isFalse);
      expect(clipFinder(), findsNothing);
      expect(
        find.ancestor(
          of: find.text('second'),
          matching: find.byType(FadeTransition),
        ),
        findsOneWidget,
      );

      await _sendBackGesture(tester, 'commitBackGesture');
      await tester.pumpAndSettle();
      expect(find.text('second'), findsNothing);
    },
    variant: TargetPlatformVariant.only(TargetPlatform.android),
  );
}
