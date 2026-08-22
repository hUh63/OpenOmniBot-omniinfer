class RemoteMcpServer {
  final String id;
  final String name;
  final String endpointUrl;
  final String bearerToken;
  final bool enabled;
  final String lastHealth;
  final String? lastError;
  final int toolCount;
  final int? lastSyncedAt;

  const RemoteMcpServer({
    required this.id,
    required this.name,
    required this.endpointUrl,
    required this.bearerToken,
    required this.enabled,
    required this.lastHealth,
    this.lastError,
    required this.toolCount,
    this.lastSyncedAt,
  });

  factory RemoteMcpServer.fromMap(Map<dynamic, dynamic> raw) {
    return RemoteMcpServer(
      id: (raw['id'] ?? '').toString(),
      name: (raw['name'] ?? '').toString(),
      endpointUrl: (raw['endpointUrl'] ?? '').toString(),
      bearerToken: (raw['bearerToken'] ?? '').toString(),
      enabled: raw['enabled'] == true,
      lastHealth: (raw['lastHealth'] ?? 'unknown').toString(),
      lastError: raw['lastError']?.toString(),
      toolCount: raw['toolCount'] is int
          ? raw['toolCount'] as int
          : int.tryParse((raw['toolCount'] ?? '0').toString()) ?? 0,
      lastSyncedAt: raw['lastSyncedAt'] is int
          ? raw['lastSyncedAt'] as int
          : int.tryParse((raw['lastSyncedAt'] ?? '').toString()),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'endpointUrl': endpointUrl,
      'bearerToken': bearerToken,
      'enabled': enabled,
      'lastHealth': lastHealth,
      'lastError': lastError,
      'toolCount': toolCount,
      'lastSyncedAt': lastSyncedAt,
    };
  }

  RemoteMcpServer copyWith({
    String? id,
    String? name,
    String? endpointUrl,
    String? bearerToken,
    bool? enabled,
    String? lastHealth,
    String? lastError,
    int? toolCount,
    int? lastSyncedAt,
  }) {
    return RemoteMcpServer(
      id: id ?? this.id,
      name: name ?? this.name,
      endpointUrl: endpointUrl ?? this.endpointUrl,
      bearerToken: bearerToken ?? this.bearerToken,
      enabled: enabled ?? this.enabled,
      lastHealth: lastHealth ?? this.lastHealth,
      lastError: lastError ?? this.lastError,
      toolCount: toolCount ?? this.toolCount,
      lastSyncedAt: lastSyncedAt ?? this.lastSyncedAt,
    );
  }
}
