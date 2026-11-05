part of 'chat_conversation_runtime_coordinator.dart';

/// UI metadata for an event that the coordinator has already projected.
/// It neither owns a turn nor replays events when a view attaches.
@immutable
class ChatAgentEventProjection {
  const ChatAgentEventProjection({
    required this.event,
    required this.conversationId,
    required this.mode,
    required this.result,
  });

  final Map<String, dynamic> event;
  final int? conversationId;
  final String? mode;
  final AgentReduceResult result;
}

extension _ChatRuntimeAgentEventSupport on ChatConversationRuntimeCoordinator {
  void _handleAgentRuntimeEvent(Map<String, dynamic> event) {
    final sessionId = acpEventSessionId(event);
    final turnId = acpEventTurnId(event);
    final params = _agentEventMap(event['params']);
    final processId = <dynamic>[
      event['processId'],
      event['process_id'],
      event['processHandle'],
      event['process_handle'],
      params?['processId'],
      params?['process_id'],
      params?['processHandle'],
      params?['process_handle'],
    ].map(_agentEventString).whereType<String>().firstOrNull;
    final conversationId =
        _agentEventInt(event['conversationId']) ??
        conversationIdForAcpEvent(sessionId: sessionId, turnId: turnId) ??
        (processId == null
            ? null
            : conversationIdForStandaloneProcess(processId));
    final mode = conversationId == null
        ? null
        : _modeForAgentRuntimeEvent(
            conversationId: conversationId,
            sessionId: sessionId,
            turnId: turnId,
            event: event,
          );
    final result = conversationId == null || mode == null
        ? const AgentReduceResult(handled: false, affectsActiveTurn: false)
        : _applyAgentEvent(
            conversationId: conversationId,
            event: event,
            mode: mode,
          );
    _latestAgentEventProjection = ChatAgentEventProjection(
      event: Map<String, dynamic>.unmodifiable(event),
      conversationId: conversationId,
      mode: mode,
      result: result,
    );
    // Publish once, after both identity attribution and item reduction. The
    // same notification updates Home's running dots and the visible chat.
    _notifyRuntimeListeners();
  }

  String _modeForAgentRuntimeEvent({
    required int conversationId,
    required String? sessionId,
    required String? turnId,
    required Map<String, dynamic> event,
  }) {
    final admittedMode = modeForAcpEvent(
      conversationId: conversationId,
      sessionId: sessionId,
      turnId: turnId,
    );
    if (admittedMode != null) return admittedMode;
    final runtimes = _runtimes.values.where(
      (runtime) => runtime.conversationId == conversationId,
    );
    if (runtimes.length == 1) return runtimes.single.mode;
    return switch (event['conversationMode']?.toString()) {
      'normal' || 'chat_only' || 'subagent' => kChatRuntimeModeNormal,
      'openclaw' => kChatRuntimeModeOpenClaw,
      _ => kChatRuntimeModeAgent,
    };
  }
}

Map<String, dynamic>? _agentEventMap(dynamic value) {
  if (value is Map<String, dynamic>) return value;
  if (value is Map) {
    return value.map((key, item) => MapEntry(key.toString(), item));
  }
  return null;
}

String? _agentEventString(dynamic value) {
  final text = value?.toString().trim() ?? '';
  return text.isEmpty ? null : text;
}

int? _agentEventInt(dynamic value) {
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '');
}
