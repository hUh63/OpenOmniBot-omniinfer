import 'package:flutter/services.dart';
import 'package:ui/models/omni_plugin_item.dart';

class SandboxPluginDashboard {
  const SandboxPluginDashboard({
    required this.pluginId,
    required this.title,
    required this.entryPath,
    required this.rootPath,
    required this.permissions,
  });

  final String pluginId;
  final String title;
  final String entryPath;
  final String rootPath;
  final Set<String> permissions;

  bool get canUseXiaowan =>
      permissions.contains('xiaowan') || permissions.contains('ai');

  factory SandboxPluginDashboard.fromMap(Map<dynamic, dynamic> raw) {
    return SandboxPluginDashboard(
      pluginId: (raw['pluginId'] ?? '').toString(),
      title: (raw['title'] ?? '').toString(),
      entryPath: (raw['entryPath'] ?? '').toString(),
      rootPath: (raw['rootPath'] ?? '').toString(),
      permissions:
          (raw['permissions'] as List<dynamic>?)
              ?.map((permission) => permission.toString())
              .toSet() ??
          const <String>{},
    );
  }
}

class OmniVlmReadiness {
  const OmniVlmReadiness({
    this.debugBuild = false,
    this.providerConfigured = false,
    this.providerName = '',
    this.model = '',
  });

  final bool debugBuild;
  final bool providerConfigured;
  final String providerName;
  final String model;

  factory OmniVlmReadiness.fromMap(Map<dynamic, dynamic>? raw) {
    return OmniVlmReadiness(
      debugBuild: raw?['debugBuild'] == true,
      providerConfigured: raw?['providerConfigured'] == true,
      providerName: (raw?['providerName'] ?? '').toString(),
      model: (raw?['model'] ?? '').toString(),
    );
  }
}

class OmniPluginService {
  static const MethodChannel _channel = MethodChannel(
    'cn.com.omnimind.bot/PluginPlatform',
  );

  static Future<List<OmniPluginItem>> listPlugins() async {
    final raw = await _channel.invokeListMethod<dynamic>('list');
    return raw
            ?.whereType<Map>()
            .map(OmniPluginItem.fromMap)
            .toList(growable: false) ??
        const <OmniPluginItem>[];
  }

  static Future<OmniPluginItem?> getPlugin(String pluginId) async {
    final plugins = await listPlugins();
    for (final plugin in plugins) {
      if (plugin.id == pluginId) return plugin;
    }
    return null;
  }

  static Future<OmniPluginItem> install(String pluginId) async {
    return _invokeState('install', <String, Object?>{'pluginId': pluginId});
  }

  static Future<OmniPluginItem> update(String pluginId) async {
    return _invokeState('update', <String, Object?>{'pluginId': pluginId});
  }

  static Future<OmniPluginItem> setEnabled(
    String pluginId,
    bool enabled,
  ) async {
    return _invokeState('setEnabled', <String, Object?>{
      'pluginId': pluginId,
      'enabled': enabled,
    });
  }

  static Future<OmniVlmReadiness> getVlmReadiness() async {
    final raw = await _channel.invokeMapMethod<dynamic, dynamic>(
      'getVlmReadiness',
    );
    return OmniVlmReadiness.fromMap(raw);
  }

  static Future<SandboxPluginDashboard> getDashboard(String pluginId) async {
    final raw = await _channel.invokeMapMethod<dynamic, dynamic>(
      'getDashboard',
      <String, Object?>{'pluginId': pluginId},
    );
    if (raw == null) {
      throw StateError('Plugin platform returned no dashboard for $pluginId');
    }
    return SandboxPluginDashboard.fromMap(raw);
  }

  static Future<Map<String, dynamic>> invokeSandbox(
    String pluginId,
    String method,
    Map<String, dynamic> params,
  ) async {
    final raw = await _channel.invokeMapMethod<dynamic, dynamic>(
      'sandboxInvoke',
      <String, Object?>{
        'pluginId': pluginId,
        'method': method,
        'params': params,
      },
    );
    return Map<String, dynamic>.from(raw ?? const <dynamic, dynamic>{});
  }

  static Future<Map<String, dynamic>> pinToHome(String pluginId) async {
    final raw = await _channel.invokeMapMethod<dynamic, dynamic>(
      'pinToHome',
      <String, Object?>{'pluginId': pluginId},
    );
    return Map<String, dynamic>.from(raw ?? const <dynamic, dynamic>{});
  }

  static Future<void> uninstall(String pluginId) async {
    await _channel.invokeMethod<bool>('uninstall', <String, Object?>{
      'pluginId': pluginId,
    });
  }

  static Future<OmniPluginItem> _invokeState(
    String method,
    Map<String, Object?> arguments,
  ) async {
    final raw = await _channel.invokeMapMethod<dynamic, dynamic>(
      method,
      arguments,
    );
    if (raw == null) {
      throw StateError('Plugin platform returned no state for $method');
    }
    return OmniPluginItem.fromMap(raw);
  }
}
