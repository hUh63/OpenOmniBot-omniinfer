import 'package:go_router/go_router.dart';
import 'pages/memory_center/memory_center_page.dart';

/// Memory妯″潡璺敱閰嶇疆
List<GoRoute> memoryRoutes = [
  GoRoute(
    path: '/memory/memory_center_page',
    name: 'memory/memory_center_page',
    builder: (context, state) {
      return MemoryCenterPage();
    },
  ),
];
