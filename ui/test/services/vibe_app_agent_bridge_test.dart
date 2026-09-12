import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/models/agent_stream_event.dart';
import 'package:ui/models/conversation_model.dart';
import 'package:ui/services/assists_core_service.dart';
import 'package:ui/services/vibe_app_agent_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{});
  });

  test(
    'starts Xiaowan task and forwards only matching stream events',
    () async {
      final gateway = _FakeGateway();
      final events = <Map<String, dynamic>>[];
      final bridge = VibeAppAgentBridge(
        pluginId: 'local.project.fitness-beast',
        appTitle: '健身兽',
        gateway: gateway,
        taskIdFactory: () => 'vibe-run-1',
        onEvent: events.add,
      );

      await bridge.initialize();
      final result = await bridge.send(<String, dynamic>{
        'text': '给我安排下周训练',
        'context': <String, dynamic>{'page': 'weekly-plan'},
      });

      expect(result['runId'], 'vibe-run-1');
      expect(result['conversationId'], 42);
      expect(gateway.startedConversationId, 42);
      expect(gateway.startedReasoningEffort, 'none');
      expect(
        gateway.startedMessage,
        contains('Plugin ID: local.project.fitness-beast'),
      );
      expect(gateway.startedMessage, contains('"page":"weekly-plan"'));
      expect(events.single['type'], 'started');

      gateway.emit(_event(taskId: 'another-task', text: 'ignore'));
      gateway.emit(_event(taskId: 'vibe-run-1', text: '第一天深蹲'));
      await Future<void>.delayed(Duration.zero);

      expect(events, hasLength(2));
      expect(events.last['type'], 'text_snapshot');
      expect(events.last['text'], '第一天深蹲');

      gateway.emit(
        _event(taskId: 'vibe-run-1', kind: AgentStreamEventKind.completed),
      );
      await Future<void>.delayed(Duration.zero);
      expect((await bridge.getState())['running'], isFalse);
      bridge.dispose();
    },
  );

  test('allows bounded reasoning but exposes only a working status', () async {
    final gateway = _FakeGateway();
    final events = <Map<String, dynamic>>[];
    final bridge = VibeAppAgentBridge(
      pluginId: 'local.project.fitness-beast',
      appTitle: '健身兽',
      gateway: gateway,
      taskIdFactory: () => 'vibe-run-reasoning',
      onEvent: events.add,
    );

    await bridge.initialize();
    await bridge.send(<String, dynamic>{
      'text': '综合最近记录制定计划',
      'reasoningEffort': 'low',
    });
    gateway.emit(
      AgentStreamEvent(
        taskId: 'vibe-run-reasoning',
        seq: 2,
        kind: AgentStreamEventKind.thinkingSnapshot,
        createdAtMs: 101,
        thinking: '不应暴露给 HTML 的原始思考',
        raw: const <String, dynamic>{
          'kind': 'thinking_snapshot',
          'thinking': '不应暴露给 HTML 的原始思考',
        },
      ),
    );
    gateway.emit(
      AgentStreamEvent(
        taskId: 'vibe-run-reasoning',
        seq: 3,
        kind: AgentStreamEventKind.thinkingSnapshot,
        createdAtMs: 102,
        thinking: '第二段也不应暴露',
      ),
    );
    await Future<void>.delayed(Duration.zero);

    expect(gateway.startedReasoningEffort, 'low');
    expect(events.last['type'], 'working');
    expect(events.last['stage'], 'analyzing');
    expect(events.last.toString(), isNot(contains('原始思考')));
    expect(events.where((event) => event['type'] == 'working'), hasLength(1));
    bridge.dispose();
  });

  test('restores conversation and cancels an active run', () async {
    SharedPreferences.setMockInitialValues(<String, Object>{
      'vibe_app_conversation_id.local.project.notes': 7,
    });
    final gateway = _FakeGateway(
      conversations: <ConversationModel>[_conversation(7)],
    );
    final events = <Map<String, dynamic>>[];
    final bridge = VibeAppAgentBridge(
      pluginId: 'local.project.notes',
      appTitle: '随身笔记',
      gateway: gateway,
      taskIdFactory: () => 'vibe-run-2',
      onEvent: events.add,
    );

    await bridge.initialize();
    final result = await bridge.send(<String, dynamic>{'text': '整理今天的记录'});
    final cancelled = await bridge.cancel(<String, dynamic>{
      'runId': result['runId'],
    });

    expect(gateway.createConversationCount, 0);
    expect(gateway.startedConversationId, 7);
    expect(cancelled['cancelled'], isTrue);
    expect(events.last['type'], 'cancelled');
    expect((await bridge.getState())['activeRunIds'], isEmpty);
    bridge.dispose();
  });
}

AgentStreamEvent _event({
  required String taskId,
  AgentStreamEventKind kind = AgentStreamEventKind.textSnapshot,
  String text = '',
}) {
  return AgentStreamEvent(
    taskId: taskId,
    seq: 1,
    kind: kind,
    createdAtMs: 100,
    text: text,
    raw: <String, dynamic>{'taskId': taskId, 'kind': kind.value, 'text': text},
  );
}

ConversationModel _conversation(int id) {
  return ConversationModel(
    id: id,
    title: 'Vibe App',
    status: 0,
    messageCount: 0,
    createdAt: 1,
    updatedAt: 1,
  );
}

class _FakeGateway implements VibeAppAgentGateway {
  _FakeGateway({this.conversations = const <ConversationModel>[]});

  final List<ConversationModel> conversations;
  final List<AgentStreamEventCallback> listeners = <AgentStreamEventCallback>[];
  int createConversationCount = 0;
  int? startedConversationId;
  String? startedMessage;
  String? startedReasoningEffort;

  @override
  void addStreamListener(AgentStreamEventCallback listener) {
    listeners.add(listener);
  }

  @override
  Future<bool> cancelTask(String taskId) async => true;

  @override
  Future<int?> createConversation({
    required String title,
    String? summary,
  }) async {
    createConversationCount += 1;
    return 42;
  }

  @override
  Future<List<ConversationModel>> getConversations() async => conversations;

  @override
  void removeStreamListener(AgentStreamEventCallback listener) {
    listeners.remove(listener);
  }

  @override
  Future<bool> startTask({
    required String taskId,
    required String userMessage,
    required int conversationId,
    required String reasoningEffort,
  }) async {
    startedConversationId = conversationId;
    startedMessage = userMessage;
    startedReasoningEffort = reasoningEffort;
    return true;
  }

  void emit(AgentStreamEvent event) {
    for (final listener in List<AgentStreamEventCallback>.from(listeners)) {
      listener(event);
    }
  }
}
