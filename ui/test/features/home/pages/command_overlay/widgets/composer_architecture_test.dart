import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  const root = 'lib/features/home/pages/command_overlay';
  const widgetRoot = '$root/widgets';

  test('composer files stay within focused responsibility budgets', () {
    const lineBudgets = <String, int>{
      '$widgetRoot/chat_input_area.dart': 550,
      '$widgetRoot/chat_input_area_composer.dart': 700,
      '$widgetRoot/chat_input_actions.dart': 300,
      '$widgetRoot/chat_input_agent_controls.dart': 500,
      '$widgetRoot/chat_input_agent_menus.dart': 700,
      '$widgetRoot/chat_input_attachments.dart': 250,
      '$widgetRoot/chat_input_context_usage.dart': 260,
      '$root/state/chat_composer_state_machine.dart': 280,
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

  test('composer facade declares every focused implementation part', () {
    final source = File('$widgetRoot/chat_input_area.dart').readAsStringSync();

    for (final part in const <String>[
      'chat_input_actions.dart',
      'chat_input_agent_controls.dart',
      'chat_input_agent_menus.dart',
      'chat_input_attachments.dart',
      'chat_input_context_usage.dart',
      'chat_input_flow_border.dart',
    ]) {
      expect(source, contains("part '$part';"));
    }
    expect(source, contains('ChatComposerStateMachine _composerStateMachine'));
  });

  test('legacy parallel interaction booleans do not return', () {
    final sources = Directory(widgetRoot)
        .listSync()
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'))
        .map((file) => file.readAsStringSync())
        .join('\n');

    for (final obsoleteField in const <String>[
      '_isComposerHovered',
      '_isOpeningAgentRunSettingsMenu',
      '_isAgentRunSettingsMenuOpen',
    ]) {
      expect(sources, isNot(contains(obsoleteField)));
    }
  });
}
