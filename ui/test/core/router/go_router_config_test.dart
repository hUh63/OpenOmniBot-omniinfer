import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:ui/core/router/go_router_config.dart';
import 'package:ui/core/router/go_router_manager.dart';

void main() {
  test('router config includes the main application routes', () {
    final routeNames = AppRouterConfig.getAllRoutes()
        .map((route) => route.name)
        .whereType<String>()
        .toSet();

    expect(routeNames, contains('home/chat'));
    expect(routeNames, contains('welcome/choice'));
    expect(routeNames, contains('task/omniflow'));
    expect(routeNames, contains('task/run_logs'));
    expect(routeNames, contains('home/first_use_tutorial/setup'));
    expect(routeNames, isNot(contains('home/first_use_tutorial')));
    expect(routeNames, isNot(contains('home/first_use_tutorial/features')));
    expect(routeNames, isNot(contains('home/first_use_tutorial/plugins')));
  });

  testWidgets('fresh launch starts in chat without forcing the tutorial', (
    tester,
  ) async {
    GoRouterManager.setInitialRoute(null);
    GoRouterManager.setSubEngine(false);
    GoRouter? router;
    addTearDown(() {
      router?.dispose();
      GoRouterManager.setInitialRoute(null);
    });

    await tester.pumpWidget(
      ProviderScope(
        child: Consumer(
          builder: (context, ref, child) {
            router ??= GoRouterManager.createRouter(ref);
            return const SizedBox.shrink();
          },
        ),
      ),
    );

    expect(router, isNotNull);
    expect(router!.routeInformationProvider.value.uri.path, '/home/chat');
  });
}
