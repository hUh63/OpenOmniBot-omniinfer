class BuiltinOfficialProviderDefinition {
  const BuiltinOfficialProviderDefinition({
    required this.key,
    required this.label,
    required this.baseUrl,
    required this.protocolType,
    required this.wireApi,
    required this.host,
  });

  final String key;
  final String label;
  final String baseUrl;
  final String protocolType;
  final String wireApi;
  final String host;

  bool matchesBaseUrl(String value) {
    final candidate = BuiltinOfficialProviderCatalog._normalizeCandidate(value);
    if (candidate == null) {
      return false;
    }
    return candidate.host.toLowerCase() == host &&
        (candidate.path.isEmpty || candidate.path == '/v1');
  }
}

class BuiltinOfficialProviderCatalog {
  static const String customKey = 'custom';

  static const List<BuiltinOfficialProviderDefinition> providers =
      <BuiltinOfficialProviderDefinition>[
        BuiltinOfficialProviderDefinition(
          key: 'deepseek',
          label: 'DeepSeek',
          baseUrl: 'https://api.deepseek.com',
          protocolType: 'deepseek',
          wireApi: 'chat_completions',
          host: 'api.deepseek.com',
        ),
        BuiltinOfficialProviderDefinition(
          key: 'mimo',
          label: 'Mimo',
          baseUrl: 'https://api.xiaomimimo.com/v1',
          protocolType: 'openai_compatible',
          wireApi: 'chat_completions',
          host: 'api.xiaomimimo.com',
        ),
        BuiltinOfficialProviderDefinition(
          key: 'moonshot',
          label: 'Kimi',
          baseUrl: 'https://api.moonshot.cn/v1',
          protocolType: 'openai_compatible',
          wireApi: 'chat_completions',
          host: 'api.moonshot.cn',
        ),
        BuiltinOfficialProviderDefinition(
          key: 'minimax',
          label: 'MiniMax',
          baseUrl: 'https://api.minimaxi.com/v1',
          protocolType: 'openai_compatible',
          wireApi: 'chat_completions',
          host: 'api.minimaxi.com',
        ),
        BuiltinOfficialProviderDefinition(
          key: 'bailian',
          label: '阿里百炼',
          baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
          protocolType: 'openai_compatible',
          wireApi: 'chat_completions',
          host: 'dashscope.aliyuncs.com',
        ),
      ];

  static BuiltinOfficialProviderDefinition? findByKey(String value) {
    final normalized = value.trim().toLowerCase();
    for (final provider in providers) {
      if (provider.key == normalized) {
        return provider;
      }
    }
    return null;
  }

  static String labelFor(String key) {
    return findByKey(key)?.label ?? 'Custom';
  }

  static String inferKey({
    required String sourceType,
    required String baseUrl,
    required String protocolType,
    required String wireApi,
  }) {
    final normalizedSourceType = sourceType.trim().toLowerCase();
    final bySourceType = findByKey(normalizedSourceType);
    if (bySourceType != null) {
      return bySourceType.key;
    }
    for (final provider in providers) {
      if (provider.matchesBaseUrl(baseUrl)) {
        return provider.key;
      }
    }
    if (protocolType.trim().toLowerCase() == 'deepseek') {
      return 'deepseek';
    }
    return customKey;
  }

  static Uri? _normalizeCandidate(String value) {
    var normalized = value.trim();
    if (normalized.isEmpty) {
      return null;
    }
    if (normalized.endsWith('#')) {
      normalized = normalized.substring(0, normalized.length - 1).trim();
    }
    normalized = normalized.replaceAll(RegExp(r'/+$'), '');
    final uri = Uri.tryParse(normalized);
    if (uri == null || !uri.hasScheme || !uri.hasAuthority) {
      return null;
    }
    if (uri.scheme != 'https') {
      return null;
    }
    return uri;
  }
}
