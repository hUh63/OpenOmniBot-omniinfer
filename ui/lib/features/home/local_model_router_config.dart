import 'package:go_router/go_router.dart';
import 'package:ui/core/router/go_router_manager.dart';
import 'package:ui/features/home/pages/companion_setting/companion_setting_page.dart';
import 'package:ui/features/home/pages/settings/imessage_setting_page.dart';
import 'pages/local_models/local_models_page.dart';

List<GoRoute> homeLocalModelRoutes = [
  GoRoute(
    path: '/home/local_models',
    name: 'home/local_models',
    pageBuilder: (context, state) => GoRouterManager.buildActivitySlidePage(
      key: state.pageKey,
      name: 'home/local_models',
      child: LocalModelsPage(
        initialTab: state.uri.queryParameters['tab'] ?? 'service',
        initialBackend: state.uri.queryParameters['backend'],
        pinnedModelId: state.uri.queryParameters['pinned'],
      ),
    ),
  ),
  GoRoute(
    path: '/home/imessage_setting',
    name: 'home/imessage_setting',
    pageBuilder: (context, state) => GoRouterManager.buildActivitySlidePage(
      key: state.pageKey,
      name: 'home/imessage_setting',
      child: const ImessageSettingPage(),
    ),
  ),
  GoRoute(
    path: '/home/companion_setting',
    name: 'home/companion_setting',
    pageBuilder: (context, state) => GoRouterManager.buildActivitySlidePage(
      key: state.pageKey,
      name: 'home/companion_setting',
      child: const CompanionSettingPage(),
    ),
  ),
];
