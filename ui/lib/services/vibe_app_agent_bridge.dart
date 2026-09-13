import 'dart:async';
import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/models/agent_stream_event.dart';
import 'package:ui/models/conversation_model.dart';
import 'package:ui/services/assists_core_service.dart';
import 'package:ui/services/conversation_service.dart';

typedef VibeAppEventSink = FutureOr<void> Function(Map<String, dynamic> event);

abstract interface class VibeAppAgentGateway {
  void addStreamListener(AgentStreamEventCallback listener);

  void removeStreamListener(AgentStreamEventCallback listener);

  Future<List<ConversationModel>> getConversations();

  Future<int?> createConversation({required String title, String? summary});

  Future<bool> startTask({
    required String taskId,
    required String userMessage,
    required int conversationId,
    required String reasoningEffort,
  });

  Future<bool> cancelTask(String taskId);
}

class XiaowanVibeAppAgentGateway implements VibeAppAgentGateway {
  const XiaowanVibeAppAgentGateway();

  @override
  void addStreamListener(AgentStreamEventCallback listener) {
    AssistsMessageService.setOnAgentStreamEventCallback(listener);
  }

  @override
  void removeStreamListener(AgentStreamEventCallback listener) {
    AssistsMessageService.removeOnAgentStreamEventCallback(listener);
  }

  @override
  Future<List<ConversationModel>> getConversations() {
    return ConversationService.getAllConversations(includeArchived: true);
  }

  @override
  Future<int?> createConversation({required String title, String? summary}) {
    return ConversationService.createConversation(
      title: title,
      summary: summary,
      mode: ConversationMode.normal,
    );
  }

  @override
  Future<bool> startTask({
    required String taskId,
    required String userMessage,
    required int conversationId,
    required String reasoningEffort,
  }) {
    return AssistsMessageService.createAgentTask(
      taskId: taskId,
      userMessage: userMessage,
      conversationId: conversationId,
      conversationMode: ConversationMode.normal.storageValue,
      reasoningEffort: reasoningEffort,
    );
  }

  @override
  Future<bool> cancelTask(String taskId) {
    return AssistsMessageService.cancelRunningTask(taskId: taskId);
  }
}

class VibeAppAgentBridge {
  VibeAppAgentBridge({
    required this.pluginId,
    required this.appTitle,
    required this.onEvent,
    VibeAppAgentGateway gateway = const XiaowanVibeAppAgentGateway(),
    String Function()? taskIdFactory,
  }) : _gateway = gateway,
       _taskIdFactory = taskIdFactory ?? _defaultTaskId;

  static const String _conversationKeyPrefix = 'vibe_app_conversation_id.';

  final String pluginId;
  final String appTitle;
  final VibeAppEventSink onEvent;
  final VibeAppAgentGateway _gateway;
  final String Function() _taskIdFactory;
  final Set<String> _activeRunIds = <String>{};
  final Set<String> _workingRunIds = <String>{};

  int? _conversationId;
  bool _initialized = false;
  bool _disposed = false;

  Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;
    _gateway.addStreamListener(_handleStreamEvent);
    await _restoreConversation();
  }

  Future<Map<String, dynamic>> send(Map<String, dynamic> params) async {
    _ensureAvailable();
    final text = (params['text'] ?? '').toString().trim();
    if (text.isEmpty) {
      throw const FormatException('app.send requires non-empty text');
    }
    final conversationId = await _ensureConversation();
    final reasoningEffort = _reasoningEffort(params['reasoningEffort']);
    final runId = _taskIdFactory();
    _activeRunIds.add(runId);
    await _emit(<String, dynamic>{
      'type': 'started',
      'runId': runId,
      'conversationId': conversationId,
      'reasoningEffort': reasoningEffort,
    });
    final accepted = await _gateway.startTask(
      taskId: runId,
      userMessage: _buildUserMessage(text, params['context']),
      conversationId: conversationId,
      reasoningEffort: reasoningEffort,
    );
    if (!accepted) {
      _activeRunIds.remove(runId);
      _workingRunIds.remove(runId);
      await _emit(<String, dynamic>{
        'type': 'error',
        'runId': runId,
        'conversationId': conversationId,
        'error': 'Xiaowan did not accept the task',
      });
      throw StateError('Xiaowan did not accept the task');
    }
    return <String, dynamic>{
      'accepted': true,
      'runId': runId,
      'conversationId': conversationId,
    };
  }

  Future<Map<String, dynamic>> cancel(Map<String, dynamic> params) async {
    _ensureAvailable();
    final runId = (params['runId'] ?? '').toString().trim();
    if (runId.isEmpty) {
      throw const FormatException('app.cancel requires runId');
    }
    if (!_activeRunIds.contains(runId)) {
      return <String, dynamic>{'cancelled': false, 'runId': runId};
    }
    final cancelled = await _gateway.cancelTask(runId);
    if (cancelled) {
      _activeRunIds.remove(runId);
      _workingRunIds.remove(runId);
      await _emit(<String, dynamic>{
        'type': 'cancelled',
        'runId': runId,
        if (_conversationId != null) 'conversationId': _conversationId,
      });
    }
    return <String, dynamic>{'cancelled': cancelled, 'runId': runId};
  }

  Future<Map<String, dynamic>> getState() async {
    _ensureAvailable();
    return <String, dynamic>{
      'pluginId': pluginId,
      'conversationId': _conversationId,
      'activeRunIds': _activeRunIds.toList(growable: false),
      'running': _activeRunIds.isNotEmpty,
    };
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    if (_initialized) {
      _gateway.removeStreamListener(_handleStreamEvent);
    }
  }

  Future<void> _restoreConversation() async {
    final preferences = await SharedPreferences.getInstance();
    final savedId = preferences.getInt('$_conversationKeyPrefix$pluginId');
    if (savedId == null || savedId <= 0) return;
    final conversations = await _gateway.getConversations();
    final exists = conversations.any(
      (conversation) =>
          conversation.id == savedId &&
          conversation.mode == ConversationMode.normal &&
          !conversation.isArchived,
    );
    if (exists) {
      _conversationId = savedId;
    } else {
      await preferences.remove('$_conversationKeyPrefix$pluginId');
    }
  }

  Future<int> _ensureConversation() async {
    final existing = _conversationId;
    if (existing != null && existing > 0) return existing;
    final created = await _gateway.createConversation(
      title: appTitle,
      summary: 'Vibe App conversation for $pluginId',
    );
    if (created == null || created <= 0) {
      throw StateError('Unable to create a Xiaowan conversation');
    }
    _conversationId = created;
    final preferences = await SharedPreferences.getInstance();
    await preferences.setInt('$_conversationKeyPrefix$pluginId', created);
    return created;
  }

  String _buildUserMessage(String text, Object? context) {
    final encodedContext = context == null ? '{}' : _encodeContext(context);
    return '''
[Vibe App request]
App: $appTitle
Plugin ID: $pluginId
Page context: $encodedContext

Act as this app's Xiaowan backend. Use the installed plugin Skill and its registered Tools when they apply. Use tools for factual reads and business writes; do not fabricate stored or external data. Return concise user-facing content suitable for rendering inside the app.

User request: $text
'''
        .trim();
  }

  String _encodeContext(Object context) {
    try {
      return jsonEncode(context);
    } catch (_) {
      return jsonEncode(context.toString());
    }
  }

  void _handleStreamEvent(AgentStreamEvent event) {
    if (_disposed || !_activeRunIds.contains(event.taskId)) return;
    if (event.kind == AgentStreamEventKind.thinkingStarted ||
        event.kind == AgentStreamEventKind.thinkingSnapshot) {
      if (!_workingRunIds.add(event.taskId)) return;
      unawaited(
        _emit(<String, dynamic>{
          'type': 'working',
          'runId': event.taskId,
          'createdAt': event.createdAtMs,
          'stage': 'analyzing',
          'label': '小万正在分析…',
        }),
      );
      return;
    }
    final payload = <String, dynamic>{
      ...event.raw,
      'type': event.kind.value,
      'runId': event.taskId,
      'createdAt': event.createdAtMs,
    };
    unawaited(_emit(payload));
    if (event.kind == AgentStreamEventKind.completed ||
        event.kind == AgentStreamEventKind.error) {
      _activeRunIds.remove(event.taskId);
      _workingRunIds.remove(event.taskId);
    }
  }

  Future<void> _emit(Map<String, dynamic> event) async {
    if (_disposed) return;
    await onEvent(event);
  }

  void _ensureAvailable() {
    if (_disposed) {
      throw StateError('Vibe App bridge is disposed');
    }
    if (!_initialized) {
      throw StateError('Vibe App bridge is not initialized');
    }
  }

  String _reasoningEffort(Object? raw) {
    final value = raw?.toString().trim().toLowerCase() ?? 'none';
    if (value == 'none' || value == 'low' || value == 'medium') return value;
    throw const FormatException(
      'app.send reasoningEffort must be none, low, or medium',
    );
  }

  static String _defaultTaskId() {
    return 'vibe-${DateTime.now().microsecondsSinceEpoch}';
  }
}
