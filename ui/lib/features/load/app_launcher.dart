import 'package:flutter/material.dart';
import 'loading_page.dart';

/// App 启动包装器
/// 先显示加载页并执行初始化，然后显示主应用
class AppLauncher extends StatefulWidget {
  final Widget app;
  final Future<void> Function()? onInitialize;

  const AppLauncher({
    Key? key,
    required this.app,
    this.onInitialize,
  }) : super(key: key);

  @override
  State<AppLauncher> createState() => _AppLauncherState();
}

class _AppLauncherState extends State<AppLauncher> {
  bool _loadingComplete = false;

  @override
  Widget build(BuildContext context) {
    if (!_loadingComplete) {
      // 使用 MaterialApp 包裹加载页，提供必要的 Widget 上下文
      return MaterialApp(
        debugShowCheckedModeBanner: false,
        home: LoadingPage(
          onInitialize: widget.onInitialize,
          onLoadingComplete: () {
            if (mounted) {
              setState(() {
                _loadingComplete = true;
              });
            }
          },
        ),
      );
    }

    return widget.app;
  }
}
