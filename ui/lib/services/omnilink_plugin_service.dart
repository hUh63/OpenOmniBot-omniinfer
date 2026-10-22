import 'dart:async';

import 'package:flutter/services.dart';

/// Sanitized inbound Agent messages emitted by the built-in OmniLink plugin.
class OmniLinkPluginService {
  static const EventChannel _events = EventChannel(
    'cn.com.omnimind.bot/OmniLinkEvents',
  );

  static Stream<Map<String, dynamic>>? _eventStream;

  /// Cached broadcast stream: `receiveBroadcastStream()` registers a new native
  /// listener on every call, and a cancel from any caller tears the whole native
  /// dispatch down. Keep a single subscription per app like the other channels.
  static Stream<Map<String, dynamic>> get events =>
      _eventStream ??= _events.receiveBroadcastStream().map((raw) {
        if (raw is Map) {
          return raw.map((key, value) => MapEntry(key.toString(), value));
        }
        return <String, dynamic>{};
      });
}
