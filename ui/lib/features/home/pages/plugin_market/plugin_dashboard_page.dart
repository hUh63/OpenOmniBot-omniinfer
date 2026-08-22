import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:ui/services/omni_plugin_service.dart';
import 'package:ui/services/vibe_app_agent_bridge.dart';
import 'package:ui/widgets/common_app_bar.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';

class PluginDashboardPage extends StatefulWidget {
  const PluginDashboardPage({super.key, required this.pluginId});

  final String pluginId;

  @override
  State<PluginDashboardPage> createState() => _PluginDashboardPageState();
}

class _PluginDashboardPageState extends State<PluginDashboardPage> {
  SandboxPluginDashboard? _dashboard;
  WebViewController? _controller;
  VibeAppAgentBridge? _agentBridge;
  String? _error;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    try {
      final dashboard = await OmniPluginService.getDashboard(widget.pluginId);
      final controller = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setBackgroundColor(Colors.transparent)
        ..addJavaScriptChannel(
          'OmniSandboxBridge',
          onMessageReceived: _handleBridgeMessage,
        )
        ..setNavigationDelegate(
          NavigationDelegate(
            onNavigationRequest: (request) =>
                _navigationDecision(dashboard, request),
          ),
        );
      final platformController = controller.platform;
      if (platformController is AndroidWebViewController) {
        await Future.wait(<Future<void>>[
          platformController.setAllowFileAccess(true),
          platformController.setAllowContentAccess(false),
        ]);
      }
      final agentBridge = VibeAppAgentBridge(
        pluginId: dashboard.pluginId,
        appTitle: dashboard.title,
        onEvent: (event) => _dispatchAppEvent(controller, event),
      );
      await agentBridge.initialize();
      if (!mounted) {
        agentBridge.dispose();
        return;
      }
      _agentBridge?.dispose();
      setState(() {
        _dashboard = dashboard;
        _controller = controller;
        _agentBridge = agentBridge;
        _error = null;
      });
      await controller.loadFile(dashboard.entryPath);
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = error.toString());
    }
  }

  NavigationDecision _navigationDecision(
    SandboxPluginDashboard dashboard,
    NavigationRequest request,
  ) {
    final uri = Uri.tryParse(request.url);
    if (uri == null || uri.scheme != 'file') {
      return NavigationDecision.prevent;
    }
    try {
      final entryPath = File(dashboard.entryPath).resolveSymbolicLinksSync();
      final frontendRoot = Directory(
        dashboard.rootPath,
      ).resolveSymbolicLinksSync();
      final requestedPath = File(uri.toFilePath()).resolveSymbolicLinksSync();
      final normalizedPath = requestedPath.toLowerCase();
      if (!normalizedPath.endsWith('.html') &&
          !normalizedPath.endsWith('.htm')) {
        return NavigationDecision.prevent;
      }
      return requestedPath == entryPath ||
              requestedPath.startsWith('$frontendRoot${Platform.pathSeparator}')
          ? NavigationDecision.navigate
          : NavigationDecision.prevent;
    } on FileSystemException {
      return NavigationDecision.prevent;
    }
  }

  Future<void> _handleBridgeMessage(JavaScriptMessage message) async {
    final controller = _controller;
    if (controller == null) return;
    String requestId = '';
    Map<String, Object?> response;
    try {
      final decoded = jsonDecode(message.message);
      if (decoded is! Map) {
        throw const FormatException('Invalid bridge request');
      }
      final request = Map<String, dynamic>.from(decoded);
      requestId = request['id']?.toString() ?? '';
      final method = request['method']?.toString() ?? '';
      final rawParams = request['params'];
      final params = rawParams is Map
          ? Map<String, dynamic>.from(rawParams)
          : const <String, dynamic>{};
      if (requestId.isEmpty || method.isEmpty) {
        throw const FormatException('Bridge request is incomplete');
      }
      final result = await _invokeBridgeMethod(method, params);
      response = <String, Object?>{
        'id': requestId,
        'ok': true,
        'result': result,
      };
    } catch (error) {
      response = <String, Object?>{
        'id': requestId,
        'ok': false,
        'error': error.toString(),
      };
    }
    await controller.runJavaScript(
      'window.__omniSandboxResolve && '
      'window.__omniSandboxResolve(${jsonEncode(response)});',
    );
  }

  Future<Map<String, dynamic>> _invokeBridgeMethod(
    String method,
    Map<String, dynamic> params,
  ) async {
    final dashboard = _dashboard;
    final agentBridge = _agentBridge;
    switch (method) {
      case 'app.send':
        _requireXiaowanPermission(dashboard);
        if (agentBridge == null) {
          throw StateError('Xiaowan bridge is not ready');
        }
        return agentBridge.send(params);
      case 'app.cancel':
        _requireXiaowanPermission(dashboard);
        if (agentBridge == null) {
          throw StateError('Xiaowan bridge is not ready');
        }
        return agentBridge.cancel(params);
      case 'app.getState':
        _requireXiaowanPermission(dashboard);
        if (agentBridge == null) {
          throw StateError('Xiaowan bridge is not ready');
        }
        return agentBridge.getState();
      default:
        return OmniPluginService.invokeSandbox(widget.pluginId, method, params);
    }
  }

  void _requireXiaowanPermission(SandboxPluginDashboard? dashboard) {
    if (dashboard?.canUseXiaowan != true) {
      throw StateError('Plugin has not declared the xiaowan permission');
    }
  }

  Future<void> _dispatchAppEvent(
    WebViewController controller,
    Map<String, dynamic> event,
  ) async {
    await controller.runJavaScript(
      'window.__omniAppEvent && window.__omniAppEvent(${jsonEncode(event)});',
    );
  }

  @override
  void dispose() {
    _agentBridge?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final dashboard = _dashboard;
    final controller = _controller;
    return Scaffold(
      appBar: CommonAppBar(
        primary: true,
        title: dashboard?.title ?? 'Plugin Dashboard',
      ),
      body: switch ((_error, controller)) {
        (final String error, _) => _DashboardError(
          message: error,
          onRetry: () {
            setState(() => _error = null);
            unawaited(_load());
          },
        ),
        (_, final WebViewController value) => WebViewWidget(controller: value),
        _ => const Center(child: CircularProgressIndicator()),
      },
    );
  }
}

class _DashboardError extends StatelessWidget {
  const _DashboardError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline_rounded, size: 40),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      ),
    );
  }
}
