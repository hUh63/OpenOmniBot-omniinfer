import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class TelegramImSettings {
  const TelegramImSettings({
    required this.enabled,
    required this.botToken,
    required this.apiBaseUrl,
    required this.allowedChatIds,
    required this.chunkSize,
    required this.dropPendingUpdates,
  });

  final bool enabled;
  final String botToken;
  final String apiBaseUrl;
  final String allowedChatIds;
  final int chunkSize;
  final bool dropPendingUpdates;

  factory TelegramImSettings.fromMap(Map<dynamic, dynamic>? map) {
    final source = map ?? const <dynamic, dynamic>{};
    return TelegramImSettings(
      enabled: source['enabled'] == true,
      botToken: _stringOrEmpty(source['botToken']),
      apiBaseUrl: _stringOrEmpty(source['apiBaseUrl']).isEmpty
          ? 'https://api.telegram.org'
          : _stringOrEmpty(source['apiBaseUrl']),
      allowedChatIds: _stringOrEmpty(source['allowedChatIds']),
      chunkSize: _intOrDefault(source['chunkSize'], 3900),
      dropPendingUpdates: source['dropPendingUpdates'] == true,
    );
  }

  Map<String, Object?> toMap() {
    return {
      'enabled': enabled,
      'botToken': botToken,
      'apiBaseUrl': apiBaseUrl,
      'allowedChatIds': allowedChatIds,
      'chunkSize': chunkSize,
      'dropPendingUpdates': dropPendingUpdates,
    };
  }

  TelegramImSettings copyWith({
    bool? enabled,
    String? botToken,
    String? apiBaseUrl,
    String? allowedChatIds,
    int? chunkSize,
    bool? dropPendingUpdates,
  }) {
    return TelegramImSettings(
      enabled: enabled ?? this.enabled,
      botToken: botToken ?? this.botToken,
      apiBaseUrl: apiBaseUrl ?? this.apiBaseUrl,
      allowedChatIds: allowedChatIds ?? this.allowedChatIds,
      chunkSize: chunkSize ?? this.chunkSize,
      dropPendingUpdates: dropPendingUpdates ?? this.dropPendingUpdates,
    );
  }
}

class WechatImSettings {
  const WechatImSettings({
    required this.enabled,
    required this.token,
    required this.baseUrl,
    required this.botType,
    required this.version,
    required this.chunkSize,
  });

  final bool enabled;
  final String token;
  final String baseUrl;
  final String botType;
  final String version;
  final int chunkSize;

  factory WechatImSettings.fromMap(Map<dynamic, dynamic>? map) {
    final source = map ?? const <dynamic, dynamic>{};
    return WechatImSettings(
      enabled: source['enabled'] == true,
      token: _stringOrEmpty(source['token']),
      baseUrl: _stringOrEmpty(source['baseUrl']).isEmpty
          ? 'https://ilinkai.weixin.qq.com'
          : _stringOrEmpty(source['baseUrl']),
      botType: _stringOrEmpty(source['botType']).isEmpty
          ? '3'
          : _stringOrEmpty(source['botType']),
      version: _stringOrEmpty(source['version']).isEmpty
          ? '1.0.0'
          : _stringOrEmpty(source['version']),
      chunkSize: _intOrDefault(source['chunkSize'], 3000),
    );
  }

  Map<String, Object?> toMap() {
    return {
      'enabled': enabled,
      'token': token,
      'baseUrl': baseUrl,
      'botType': botType,
      'version': version,
      'chunkSize': chunkSize,
    };
  }

  WechatImSettings copyWith({
    bool? enabled,
    String? token,
    String? baseUrl,
    String? botType,
    String? version,
    int? chunkSize,
  }) {
    return WechatImSettings(
      enabled: enabled ?? this.enabled,
      token: token ?? this.token,
      baseUrl: baseUrl ?? this.baseUrl,
      botType: botType ?? this.botType,
      version: version ?? this.version,
      chunkSize: chunkSize ?? this.chunkSize,
    );
  }
}

class ImConnectorStatus {
  const ImConnectorStatus({
    required this.channel,
    required this.enabled,
    required this.running,
    required this.connected,
    required this.accountLabel,
    required this.lastError,
    this.sdkAvailable,
  });

  final String channel;
  final bool enabled;
  final bool running;
  final bool connected;
  final String accountLabel;
  final String lastError;
  final bool? sdkAvailable;

  factory ImConnectorStatus.fromMap(Map<dynamic, dynamic>? map) {
    final source = map ?? const <dynamic, dynamic>{};
    return ImConnectorStatus(
      channel: _stringOrEmpty(source['channel']),
      enabled: source['enabled'] == true,
      running: source['running'] == true,
      connected: source['connected'] == true,
      accountLabel: _stringOrEmpty(source['accountLabel']),
      lastError: _stringOrEmpty(source['lastError']),
      sdkAvailable: source['sdkAvailable'] is bool
          ? source['sdkAvailable'] as bool
          : null,
    );
  }
}

class ImChannelState {
  const ImChannelState({
    required this.telegram,
    required this.wechat,
    required this.running,
    required this.pendingRunCount,
    required this.sessionCount,
    required this.connectors,
  });

  final TelegramImSettings telegram;
  final WechatImSettings wechat;
  final bool running;
  final int pendingRunCount;
  final int sessionCount;
  final List<ImConnectorStatus> connectors;

  ImConnectorStatus? connector(String channel) {
    for (final item in connectors) {
      if (item.channel == channel) return item;
    }
    return null;
  }

  factory ImChannelState.fromMap(Map<dynamic, dynamic>? map) {
    final source = map ?? const <dynamic, dynamic>{};
    final settings = source['settings'] as Map<dynamic, dynamic>? ?? const {};
    final status = source['status'] as Map<dynamic, dynamic>? ?? const {};
    final rawConnectors = status['connectors'];
    return ImChannelState(
      telegram: TelegramImSettings.fromMap(
        settings['telegram'] as Map<dynamic, dynamic>?,
      ),
      wechat: WechatImSettings.fromMap(
        settings['wechat'] as Map<dynamic, dynamic>?,
      ),
      running: status['running'] == true,
      pendingRunCount: _intOrDefault(status['pendingRunCount'], 0),
      sessionCount: _intOrDefault(status['sessionCount'], 0),
      connectors: rawConnectors is List
          ? rawConnectors
                .whereType<Map>()
                .map(ImConnectorStatus.fromMap)
                .toList(growable: false)
          : const <ImConnectorStatus>[],
    );
  }
}

class ImChannelService {
  static const MethodChannel _channel = MethodChannel(
    'cn.com.omnimind.bot/ImChannel',
  );

  static Future<ImChannelState?> state() async {
    try {
      final raw = await _channel.invokeMethod<Map<dynamic, dynamic>>('state');
      return ImChannelState.fromMap(raw);
    } on PlatformException catch (e) {
      debugPrint('IM state failed: ${e.message}');
      return null;
    }
  }

  static Future<ImChannelState?> refresh() async {
    final raw = await _channel.invokeMethod<Map<dynamic, dynamic>>('refresh');
    return ImChannelState.fromMap(raw);
  }

  static Future<ImChannelState?> saveTelegram(
    TelegramImSettings settings,
  ) async {
    final raw = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'saveTelegram',
      settings.toMap(),
    );
    return ImChannelState.fromMap(raw);
  }

  static Future<ImChannelState?> saveWechat(WechatImSettings settings) async {
    final raw = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'saveWechat',
      settings.toMap(),
    );
    return ImChannelState.fromMap(raw);
  }

  static Future<ImChannelState?> setChannelEnabled(
    String channel,
    bool enabled,
  ) async {
    final raw = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'setChannelEnabled',
      {'channel': channel, 'enabled': enabled},
    );
    return ImChannelState.fromMap(raw);
  }

  static Future<Map<dynamic, dynamic>?> requestWechatQr() async {
    return _channel.invokeMethod<Map<dynamic, dynamic>>('requestWechatQr');
  }

  static Future<ImChannelState?> clearPeerSessions() async {
    final raw = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'clearPeerSessions',
    );
    return ImChannelState.fromMap(raw);
  }
}

String _stringOrEmpty(Object? value) => value?.toString() ?? '';

int _intOrDefault(Object? value, int fallback) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? fallback;
  return fallback;
}
