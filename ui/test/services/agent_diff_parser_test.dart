import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ui/features/home/pages/command_overlay/widgets/cards/agent_diff_viewer.dart';
import 'package:ui/services/assists_core_service.dart';
import 'package:ui/services/agent_diff_parser.dart';

void main() {
  const diffText = '''
diff --git a/lib/main.dart b/lib/main.dart
--- a/lib/main.dart
+++ b/lib/main.dart
@@ -1,2 +1,2 @@
-old line
+new line
 same line
''';

  test('parseAgentDiffText groups file hunks and counts changes', () {
    final summary = parseAgentDiffText(diffText);

    expect(summary.files, hasLength(1));
    expect(summary.additions, 1);
    expect(summary.deletions, 1);
    expect(summary.primaryPath, 'lib/main.dart');
    expect(
      summary.files.single.lines.any(
        (line) => line.kind == AgentDiffLineKind.add,
      ),
      isTrue,
    );
    expect(
      summary.files.single.lines.any(
        (line) => line.kind == AgentDiffLineKind.remove,
      ),
      isTrue,
    );
    expect(summarizeAgentDiff(summary), '1 file · +1 -1');
  });

  test('extractAgentDiffText finds nested diff payloads', () {
    final extracted = extractAgentDiffText({
      'result': {'patch': diffText},
    });

    expect(extracted, isNotNull);
    expect(extracted, contains('diff --git'));
  });

  test('extractAgentDiffText normalizes hunk-only change payloads', () {
    const hunkOnlyDiff = '''
@@ -1,2 +1,2 @@
-old line
+new line
 same line
''';
    final extracted = extractAgentDiffText({
      'changes': {
        'path': '/repo/lib/main.dart',
        'kind': {'type': 'update'},
        'diff': hunkOnlyDiff,
      },
    });

    expect(extracted, isNotNull);
    expect(extracted, contains('diff --git'));
    expect(extracted, contains('/repo/lib/main.dart'));

    final summary = parseAgentDiffText(extracted!);
    expect(summary.primaryPath, '/repo/lib/main.dart');
    expect(summary.additions, 1);
    expect(summary.deletions, 1);
  });

  test('extractAgentDiffText reads hunk-only changes from raw tool events', () {
    final event = AgentToolEventData.fromMap({
      'toolName': 'codex.file',
      'toolType': 'builtin',
      'type': 'fileChange',
      'changes': jsonEncode({
        'path': '/repo/ui/test/services/agent_diff_parser_test.dart',
        'kind': {'type': 'update', 'move_path': null},
        'diff': '''
@@ -1,2 +1,2 @@
-old line
+new line
 same line
''',
      }),
      'status': 'completed',
    });

    final extracted = extractAgentDiffText({
      ...event.raw,
      'toolName': event.toolName,
      'toolType': event.toolType,
      'argsJson': event.argsJson,
      'rawResultJson': event.rawResultJson,
      'resultPreviewJson': event.resultPreviewJson,
    });

    expect(extracted, isNotNull);
    final summary = parseAgentDiffText(extracted!);
    expect(
      summary.primaryPath,
      '/repo/ui/test/services/agent_diff_parser_test.dart',
    );
    expect(summary.additions, 1);
    expect(summary.deletions, 1);
  });

  test('AgentToolEventData normalizes codex app-server tool calls', () {
    final event = AgentToolEventData.fromMap({
      'type': 'mcpToolCall',
      'tool': 'mcp__filesystem__read_file',
      'arguments': {'path': 'README.md'},
      'status': 'completed',
    });

    expect(event.toolType, 'workspace');
    expect(event.uiStyle, 'agent_tool');
    expect(event.toolTitle, 'Read README.md');
    expect(event.status, 'success');
    expect(event.argsJson, contains('README.md'));
  });

  testWidgets('AgentDiffViewer renders diff summary and file body', (
    tester,
  ) async {
    final summary = parseAgentDiffText(diffText);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AgentDiffViewer(
            summary: summary,
            padding: const EdgeInsets.all(12),
          ),
        ),
      ),
    );

    expect(find.text('lib/main.dart'), findsNWidgets(2));
    expect(find.text('1 个文件 · +1 -1'), findsOneWidget);
    expect(
      find.textContaining('-old line', findRichText: true),
      findsOneWidget,
    );
    expect(
      find.textContaining('+new line', findRichText: true),
      findsOneWidget,
    );
    expect(
      find.textContaining('same line', findRichText: true),
      findsOneWidget,
    );
  });

  testWidgets('AgentDiffViewer renders in dark theme', (tester) async {
    final summary = parseAgentDiffText(diffText);

    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData(brightness: Brightness.dark),
        home: Scaffold(
          body: AgentDiffViewer(
            summary: summary,
            padding: const EdgeInsets.all(12),
          ),
        ),
      ),
    );

    expect(find.text('lib/main.dart'), findsNWidgets(2));
    expect(
      find.textContaining('+new line', findRichText: true),
      findsOneWidget,
    );
  });

  testWidgets('AgentDiffViewer horizontal drag is kept inside diff surface', (
    tester,
  ) async {
    final summary = parseAgentDiffText(diffText);
    var parentHorizontalDragUpdates = 0;
    var currentPage = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GestureDetector(
            onHorizontalDragUpdate: (_) {
              parentHorizontalDragUpdates += 1;
            },
            child: PageView(
              onPageChanged: (page) {
                currentPage = page;
              },
              children: [
                AgentDiffViewer(
                  summary: summary,
                  padding: const EdgeInsets.all(12),
                  showOverview: false,
                ),
                const Center(child: Text('workspace page')),
              ],
            ),
          ),
        ),
      ),
    );

    await tester.drag(find.text('lib/main.dart'), const Offset(-260, 0));
    await tester.pumpAndSettle();

    expect(parentHorizontalDragUpdates, 0);
    expect(currentPage, 0);
    expect(find.text('workspace page'), findsNothing);
  });
}
