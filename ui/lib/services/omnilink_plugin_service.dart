import 'dart:async';

import 'package:flutter/services.dart';

/// Sanitized inbound Agent messages emitted by the built-in OmniLink plugin.
class OmniLinkPluginService {
  static const EventChannel _events = EventChannel(
    'cn.com.omnimind.bot/OmniLinkEvents',
  );

  static Stream<Map<String, dynamic>> get events =>
      _events.receiveBroadcastStream().map((raw) {
        if (raw is Map) {
          return raw.map((key, value) => MapEntry(key.toString(), value));
        }
        return <String, dynamic>{};
      });
}
