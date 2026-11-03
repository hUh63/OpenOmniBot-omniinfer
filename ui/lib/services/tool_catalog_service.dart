import 'package:flutter/services.dart';

class CatalogTool {
  const CatalogTool({
    required this.name,
    required this.displayName,
    required this.description,
    required this.group,
    required this.source,
    required this.status,
  });

  final String name;
  final String displayName;
  final String description;
  final String group;
  final String source;
  final String status;

  factory CatalogTool.fromMap(Map<dynamic, dynamic> raw) => CatalogTool(
    name: raw['name'] as String,
    displayName: raw['displayName'] as String,
    description: raw['description'] as String,
    group: raw['group'] as String,
    source: raw['source'] as String? ?? '',
    status: raw['status'] as String,
  );

  bool matches(String query) =>
      '$name $displayName $description $source'.toLowerCase().contains(query);
}

class CatalogServer {
  CatalogServer.fromMap(Map<dynamic, dynamic> raw)
    : name = raw['source'] as String,
      status = raw['status'] as String,
      tools = (raw['tools'] as List<dynamic>)
          .map((item) => CatalogTool.fromMap(item as Map))
          .toList();

  final String name;
  final String status;
  final List<CatalogTool> tools;
}

/// Configuration catalog, not the active ACP session's negotiated tool set.
class ToolCatalogService {
  static Future<List<CatalogTool>> localTools() async {
    final rows = await const MethodChannel('cn.com.omnimind.bot/PluginPlatform')
        .invokeListMethod<dynamic>('listTools');
    return (rows ?? []).map((row) => CatalogTool.fromMap(row as Map)).toList();
  }

  static Future<List<CatalogServer>> remoteTools() async {
    final rows = await const MethodChannel(
      'cn.com.omnimind.bot/RemoteMcpConfig',
    ).invokeListMethod<dynamic>('listToolCatalog');
    return (rows ?? [])
        .map((row) => CatalogServer.fromMap(row as Map))
        .toList();
  }
}
