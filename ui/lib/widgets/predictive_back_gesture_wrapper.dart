import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show PredictiveBackEvent;
import 'package:ui/services/storage_service.dart';

/// 与 [PageRoute] transitionsBuilder 一致的转场构建函数签名。
typedef PredictiveBackTransitionBuilder =
    Widget Function(
      BuildContext context,
      Animation<double> animation,
      Animation<double> secondaryAnimation,
      Widget child,
    );

/// 预测性返回手势桥接器。
///
/// 视觉风格对齐 Miuix(miuix-navigation3-ui NavDisplay.kt 的
/// defaultPredictivePopTransitionSpec):顶层页随手势整体向边缘滑出、上一页
/// 平移进入,手势期间顶层页加圆角 —— 不做官方 Material 的卡片式整体缩小。
///
/// 实现上不做任何自绘动画数学:
/// - 转场渲染完全交给框架官方的 [CupertinoPageTransition](滑动+视差,
///   与 iOS 返回手势同款,本来就设计为可被手势驱动);
/// - 本组件仅以 WidgetsBindingObserver 把手势事件转发给所在 ModalRoute
///   (与框架 _PredictiveBackGestureDetector 相同的公开 API 路径:
///   TransitionRoute.handleStartBackGesture / handleUpdateBackGestureProgress
///   / handleCommitBackGesture / handleCancelBackGesture),手势进度由框架
///   直接驱动路由动画控制器,转场自然跟随手指;
/// - 手势进行中(popGestureInProgress)给顶层页加圆角、并把转场切换为
///   linearTransition(与手指 1:1 映射),其余时刻转场行为不变;
/// - 提交时以 iOS 方式收尾(见 handleCommitBackGesture 注释),避免框架
///   TransitionRoute._handleDragEnd 的 reverse(from: 1.0) 重播导致的回弹。
///
/// 开关关闭(StorageService.isPredictiveBackEnabled() == false)或非 Android
/// 平台时不消费任何手势事件,回退到 [transitionBuilder] 指定的应用原有转场,
/// 行为与旧版完全一致。
class PredictiveBackGestureWrapper extends StatefulWidget {
  const PredictiveBackGestureWrapper({
    super.key,
    required this.animation,
    required this.secondaryAnimation,
    required this.transitionBuilder,
    required this.child,
  });

  /// 路由的主动画(手势期间被手势进度直接驱动)。
  final Animation<double> animation;

  /// 路由的次动画(驱动下方路由的入场)。
  final Animation<double> secondaryAnimation;

  /// 开关关闭或非 Android 平台时使用的自定义转场(旧版行为)。
  final PredictiveBackTransitionBuilder transitionBuilder;

  final Widget child;

  @override
  State<PredictiveBackGestureWrapper> createState() =>
      _PredictiveBackGestureWrapperState();
}

class _PredictiveBackGestureWrapperState
    extends State<PredictiveBackGestureWrapper>
    with WidgetsBindingObserver, SingleTickerProviderStateMixin {
  /// 手势期间顶层页的圆角(与框架对设备物理圆角的估值一致,
  /// 见 flutter/flutter#97349)。
  static final BorderRadius _kGestureBorderRadius = BorderRadius.circular(32.0);

  /// 提交收尾动画满时长对应的控制器区间(0→1,即整页滑出)。
  static const int _kSettleMilliseconds = 300;

  ModalRoute<dynamic>? _route;

  /// 当前是否正在处理一次返回手势(只有 start 阶段成功消费后才为 true)。
  bool _handlingGesture = false;

  /// 提交收尾动画:把路由控制器从松手位置顺势补到 0(完全滑出)后再 pop。
  late final AnimationController _settleController = AnimationController(
    vsync: this,
  );
  CurvedAnimation? _settleCurve;

  /// 收尾动画起始时路由控制器的值(1 = 页面完全在场,0 = 完全退出)。
  double _settleStartValue = 0.0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _settleController
      ..addListener(_onSettleTick)
      ..addStatusListener(_onSettleStatus);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _route = ModalRoute.of(context);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _settleCurve?.dispose();
    _settleController.dispose();
    super.dispose();
  }

  bool get _predictiveBackEnabled {
    return defaultTargetPlatform == TargetPlatform.android &&
        StorageService.isPredictiveBackEnabled();
  }

  // Begin WidgetsBindingObserver.

  @override
  bool handleStartBackGesture(PredictiveBackEvent event) {
    final route = _route;
    // 注:无需再判断 route is PredictiveBackRoute —— ModalRoute 继承的
    // TransitionRoute 在框架中已 implements PredictiveBackRoute
    // (widgets/routes.dart:111),该判断恒为 true。
    if (event.isButtonEvent ||
        !_predictiveBackEnabled ||
        route == null ||
        !route.isCurrent ||
        !route.popGestureEnabled) {
      return false;
    }

    // 新手势开始,丢弃可能未完成的收尾动画。
    _settleController.stop();
    _handlingGesture = true;
    // 与框架 detector 一致:事件进度(0→1 表示手势完成度)需取反后驱动
    // 路由动画控制器(1.0 = 页面完全在场,0.0 = 完全退出)。
    route.handleStartBackGesture(progress: 1 - event.progress);
    // 触发重建,应用圆角与 linearTransition。
    setState(() {});
    return true;
  }

  @override
  void handleUpdateBackGestureProgress(PredictiveBackEvent event) {
    if (!_handlingGesture) {
      return;
    }
    _route?.handleUpdateBackGestureProgress(progress: 1 - event.progress);
  }

  @override
  void handleCommitBackGesture() {
    if (!_handlingGesture) {
      return;
    }
    _handlingGesture = false;
    final route = _route;
    if (route == null) {
      return;
    }
    final double value = route.animation?.value ?? 0.0;
    if (value <= 0.01) {
      route.handleCommitBackGesture();
      return;
    }
    // 不能直接 route.handleCommitBackGesture():TransitionRoute._handleDragEnd
    // (widgets/routes.dart:592-623)在 pop 后会用 reverse(from: upperBound)
    // 把退出动画从 1.0 重播,CupertinoPageTransition 的位置与控制器 1:1 映射,
    // 页面会先跳回满屏再滑出(回弹)。官方卡片视觉用自己的 commit 曲线掩盖了
    // 这一点。这里按 iOS 手势收尾方式:先把控制器从松手位置补动画到 0
    // (页面顺势滑出),再提交 pop(此时控制器已在 0,pop 即时完成,不会重播)。
    _settleStartValue = value;
    _settleCurve?.dispose();
    _settleCurve = CurvedAnimation(
      parent: _settleController,
      curve: Curves.easeOutCubic,
    );
    _settleController.duration = Duration(
      milliseconds: (value * _kSettleMilliseconds)
          .clamp(80.0, _kSettleMilliseconds.toDouble())
          .round(),
    );
    _settleController.forward(from: 0.0);
  }

  @override
  void handleCancelBackGesture() {
    if (!_handlingGesture) {
      return;
    }
    _handlingGesture = false;
    _settleController.stop();
    _route?.handleCancelBackGesture();
  }

  // End WidgetsBindingObserver.

  void _onSettleTick() {
    final route = _route;
    if (!mounted || route == null) {
      return;
    }
    final t = _settleCurve?.value ?? 1.0;
    route.handleUpdateBackGestureProgress(
      progress: (1 - t) * _settleStartValue,
    );
  }

  void _onSettleStatus(AnimationStatus status) {
    if (status == AnimationStatus.completed) {
      // 控制器已到 0(页面完全滑出),提交 pop;此时 pop 不再触发
      // reverse(from: upperBound) 的重播。
      _route?.handleCommitBackGesture();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_predictiveBackEnabled) {
      return widget.transitionBuilder(
        context,
        widget.animation,
        widget.secondaryAnimation,
        widget.child,
      );
    }
    final route = _route;
    final gesturing = route?.popGestureInProgress ?? false;
    // ClipRRect 常驻保持子树结构稳定(圆角归零时无视觉效果),
    // 仅被弹出的顶层页(popGestureInProgress 且 isCurrent)在手势期间加圆角。
    final clip = gesturing && route!.isCurrent
        ? _kGestureBorderRadius
        : BorderRadius.zero;
    // The page is already being composited every frame by the predictive
    // transition. Avoid an anti-aliased full-screen clip here: on a complex
    // page it adds a raster/composition pass and can make the gesture miss
    // display deadlines. Hard clipping keeps the rounded shape while using
    // the cheaper clip path during the gesture; no clip is needed otherwise.
    return ClipRRect(
      clipBehavior: gesturing ? Clip.hardEdge : Clip.none,
      borderRadius: clip,
      child: CupertinoPageTransition(
        primaryRouteAnimation: widget.animation,
        secondaryRouteAnimation: widget.secondaryAnimation,
        // 手势期间与手指 1:1 线性映射,普通导航保持原有曲线。
        linearTransition: gesturing,
        // Keep the page content in its own retained layer. During a back
        // gesture only the route transform changes; without a boundary the
        // large settings/chat subtree can be repainted for every progress
        // sample before the raster thread submits the next surface.
        child: RepaintBoundary(child: widget.child),
      ),
    );
  }
}
