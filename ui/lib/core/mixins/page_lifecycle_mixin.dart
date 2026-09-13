import 'package:flutter/material.dart';
import 'package:ui/core/router/go_router_manager.dart';

/// 页面生命周期 Mixin
/// 
/// 解决 WidgetsBindingObserver 会触发所有页面的问题，
/// 只有当前可见的页面才会响应 onPageResumed 回调
/// 
/// 使用方式：
/// ```dart
/// class MyPageState extends State<MyPage> 
///     with WidgetsBindingObserver, PageLifecycleMixin<MyPage> {
///   
///   @override
///   void onPageResumed() {
///     // 页面可见时的逻辑
///     _loadData();
///   }
///   
///   @override
///   void onPagePaused() {
///     // 页面不可见时的逻辑（可选）
///   }
/// }
/// ```
mixin PageLifecycleMixin<T extends StatefulWidget> 
    on State<T>, WidgetsBindingObserver 
    implements RouteAware {
  
  bool _isPageVisible = false;
  ModalRoute<dynamic>? _subscribedRoute;
  
  /// 当页面可见时调用（从后台回到前台 + 页面在路由栈顶部）
  void onPageResumed() {}
  
  /// 当页面不可见时调用（可选实现）
  void onPagePaused() {}

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // 注册到 RouteObserver
    final route = ModalRoute.of(context);
    if (route is PageRoute) {
      // 避免在依赖变化时重复订阅不同 route，导致可见性状态异常
      if (_subscribedRoute != route) {
        if (_subscribedRoute != null) {
          GoRouterManager.routeObserver.unsubscribe(this);
        }
        _subscribedRoute = route;
        GoRouterManager.routeObserver.subscribe(this, route);
      }

      // 关键：订阅发生在 route 已经入栈之后，RouteObserver 不会补发 didPush
      // 因此这里需要主动同步一次当前可见性状态
      _isPageVisible = route.isCurrent;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    if (_subscribedRoute != null) {
      GoRouterManager.routeObserver.unsubscribe(this);
      _subscribedRoute = null;
    }
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // 只有当页面可见时才触发回调
    if (state == AppLifecycleState.resumed && _isPageVisible) {
      onPageResumed();
    } else if (state == AppLifecycleState.paused && _isPageVisible) {
      onPagePaused();
    }
  }

  // RouteAware 回调：页面进入前台（push 进来或从其他页面 pop 回来）
  @override
  void didPush() {
    _isPageVisible = true;
  }

  @override
  void didPopNext() {
    _isPageVisible = true;
    // 从其他页面返回时，如果应用在前台，触发 resume
    if (WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) {
      onPageResumed();
    }
  }

  // RouteAware 回调：页面离开前台（push 到其他页面或被 pop）
  @override
  void didPushNext() {
    _isPageVisible = false;
    onPagePaused();
  }

  @override
  void didPop() {
    _isPageVisible = false;
  }
}
