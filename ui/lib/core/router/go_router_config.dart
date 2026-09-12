import 'package:go_router/go_router.dart';
import '../../features/home/router_config.dart';
import '../../features/task/router_config.dart';
import '../../features/welcome/router_config.dart';
import '../../features/memory/router_config.dart';
import '../../features/my/router_config.dart';

class AppRouterConfig {
  static List<GoRoute> _extraHomeRoutes = const [];
  static List<GoRoute> _extraWelcomeRoutes = const [];

  static void configure({
    List<GoRoute> extraHomeRoutes = const [],
    List<GoRoute> extraWelcomeRoutes = const [],
  }) {
    _extraHomeRoutes = extraHomeRoutes;
    _extraWelcomeRoutes = extraWelcomeRoutes;
  }

  static List<GoRoute> getAllRoutes() {
    return [
      ...homeRoutes,
      ..._extraHomeRoutes,
      ...taskRoutes,
      ...welcomeRoutes,
      ..._extraWelcomeRoutes,
      ...memoryRoutes,
      ...myRoutes,
    ];
  }
}
