import 'package:ui/models/conversation_model.dart';
import 'package:ui/models/conversation_thread_target.dart';

const String kNativeRouteFlag = '__from_native__';

ConversationMode _parseConversationMode(String? rawValue) {
  return ConversationMode.fromStorageValue(rawValue);
}

/// Decodes the shared chat destination at the route boundary.
/// Both native links and Flutter navigation preserve the same thread identity.
ConversationThreadTarget? parseConversationRouteTarget(Uri uri, Object? extra) {
  final queryConversationId =
      uri.queryParameters['conversationId']?.trim() ?? '';
  final queryMode = _parseConversationMode(uri.queryParameters['mode']);
  final queryRequestKey = uri.queryParameters['requestKey']?.trim();
  final queryAgentId = uri.queryParameters['agentId']?.trim();
  if (queryConversationId.isNotEmpty) {
    if (queryConversationId == 'new' || queryConversationId == '__new__') {
      return ConversationThreadTarget.newConversation(
        mode: queryMode,
        agentId: queryAgentId?.isEmpty == true ? null : queryAgentId,
        fromNativeRoute: true,
        requestKey: queryRequestKey?.isEmpty == true ? null : queryRequestKey,
      );
    }
    final conversationId = int.tryParse(queryConversationId);
    if (conversationId != null) {
      return ConversationThreadTarget.existing(
        conversationId: conversationId,
        mode: queryMode,
        agentId: queryAgentId?.isEmpty == true ? null : queryAgentId,
        fromNativeRoute: true,
        requestKey: queryRequestKey?.isEmpty == true ? null : queryRequestKey,
      );
    }
  }

  if (extra is ConversationThreadTarget) {
    return extra;
  }

  final argsFromExtra = extra is List<String> ? extra : const <String>[];
  if (argsFromExtra.isEmpty) {
    return null;
  }

  final first = argsFromExtra.first.trim();
  final requestKey = argsFromExtra
      .skip(1)
      .map((item) => item.trim())
      .firstWhere(
        (item) =>
            item.isNotEmpty &&
            item != kNativeRouteFlag &&
            !item.startsWith('mode=') &&
            ConversationMode.values.every(
              (mode) => mode.storageValue != item.toLowerCase(),
            ),
        orElse: () => '',
      );
  final modeRaw = argsFromExtra
      .skip(1)
      .map((item) => item.trim())
      .firstWhere(
        (item) =>
            item.startsWith('mode=') ||
            ConversationMode.values.any(
              (mode) => mode.storageValue == item.toLowerCase(),
            ),
        orElse: () => '',
      );
  final mode = modeRaw.startsWith('mode=')
      ? _parseConversationMode(modeRaw.substring(5))
      : _parseConversationMode(modeRaw);
  final fromNativeRoute = argsFromExtra.contains(kNativeRouteFlag);

  if (first == 'new' || first == '__new__') {
    return ConversationThreadTarget.newConversation(
      mode: mode,
      fromNativeRoute: fromNativeRoute,
      requestKey: requestKey.isEmpty ? null : requestKey,
    );
  }

  final conversationId = int.tryParse(first);
  if (conversationId == null) {
    return null;
  }
  return ConversationThreadTarget.existing(
    conversationId: conversationId,
    mode: mode,
    fromNativeRoute: fromNativeRoute,
    requestKey: requestKey.isEmpty ? null : requestKey,
  );
}
