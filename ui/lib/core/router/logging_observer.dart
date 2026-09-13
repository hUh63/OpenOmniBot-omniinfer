import 'package:flutter/material.dart';

class LoggingRouterObserver extends NavigatorObserver {
  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    print('[Router] didPush: ${route.settings.name} (previous: ${previousRoute?.settings.name})');
  }

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) {
    print('[Router] didPop: ${route.settings.name} (previous: ${previousRoute?.settings.name})');
  }

  @override
  void didRemove(Route<dynamic> route, Route<dynamic>? previousRoute) {
    print('[Router] didRemove: ${route.settings.name} (previous: ${previousRoute?.settings.name})');
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    print('[Router] didReplace: ${newRoute?.settings.name} (old: ${oldRoute?.settings.name})');
  }
}
