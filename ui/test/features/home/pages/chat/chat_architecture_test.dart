import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  const chatRoot = 'lib/features/home/pages/chat';

  test('chat composition files stay within their responsibility budgets', () {
    const lineBudgets = <String, int>{
      '$chatRoot/chat_page.dart': 2200,
      '$chatRoot/chat_page_agent.dart': 2200,
      '$chatRoot/chat_page_ui.dart': 2900,
      '$chatRoot/services/chat_conversation_runtime_coordinator.dart': 1300,
      '$chatRoot/widgets/chat_widgets.dart': 100,
    };

    for (final entry in lineBudgets.entries) {
      final lineCount = File(entry.key).readAsLinesSync().length;
      expect(
        lineCount,
        lessThanOrEqualTo(entry.value),
        reason: '${entry.key} is becoming a mixed-responsibility hotspot',
      );
    }
  });

  test('chat page keeps per-mode values in ChatPageModeState', () {
    final source = File('$chatRoot/chat_page.dart').readAsStringSync();

    expect(source, isNot(contains('Map<ChatPageMode,')));
    expect(source, contains('List<ChatPageModeState> _modeStates'));
  });

  test(
    'runtime and widget facades declare their focused implementation parts',
    () {
      final runtimeSource = File(
        '$chatRoot/services/chat_conversation_runtime_coordinator.dart',
      ).readAsStringSync();
      final widgetSource = File(
        '$chatRoot/widgets/chat_widgets.dart',
      ).readAsStringSync();

      for (final part in const <String>[
        'chat_runtime_agent_stream_support.dart',
        'chat_runtime_event_ingress.dart',
        'chat_runtime_message_support.dart',
        'chat_runtime_streaming_support.dart',
        'chat_runtime_thinking_support.dart',
        'chat_runtime_tool_support.dart',
      ]) {
        expect(runtimeSource, contains("part '$part';"));
      }
      for (final part in const <String>[
        'chat_app_bar.dart',
        'chat_input_wrapper.dart',
        'chat_message_list.dart',
        'chat_mode_slider.dart',
      ]) {
        expect(widgetSource, contains("part '$part';"));
      }
    },
  );
}
