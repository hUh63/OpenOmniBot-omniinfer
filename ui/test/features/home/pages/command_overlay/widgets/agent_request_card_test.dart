import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/features/home/pages/command_overlay/widgets/cards/agent_request_card.dart';
import 'package:ui/services/storage_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const requestIdentity = 'request-1.request-1-card.mode.1000';
  const requestStorageKey = 'agent_request_response.$requestIdentity';
  const agentRuntimeChannel = MethodChannel('cn.com.omnimind.bot/AgentRuntime');
  const assistCoreChannel = MethodChannel(
    'cn.com.omnimind.bot/AssistCoreEvent',
  );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(assistCoreChannel, (call) async => null);
  });

  tearDown(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(agentRuntimeChannel, null);
    messenger.setMockMethodCallHandler(assistCoreChannel, null);
  });

  testWidgets('renders requestUserInput options and submits selection', (
    tester,
  ) async {
    MethodCall? submittedCall;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(agentRuntimeChannel, (call) async {
      submittedCall = call;
      return <String, dynamic>{'ok': true};
    });

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: AgentRequestCard(cardData: _requestCardData())),
      ),
    );

    expect(find.text('Plan'), findsOneWidget);
    expect(find.text('Chat'), findsOneWidget);
    expect(find.byType(TextField), findsOneWidget);
    expect(find.text('No, tell Claude Code how to adjust'), findsOneWidget);
    expect(find.text('ESC'), findsNothing);

    await tester.tap(find.text('Chat'));
    await tester.pump();
    await tester.tap(find.text('Submit ↵'));
    await tester.pumpAndSettle();

    expect(submittedCall?.method, 'respondToServerRequest');
    expect(submittedCall?.arguments, containsPair('requestId', 'request-1'));
    final arguments = Map<String, dynamic>.from(
      submittedCall!.arguments as Map,
    );
    final response = Map<String, dynamic>.from(arguments['response'] as Map);
    final answers = Map<String, dynamic>.from(response['answers'] as Map);
    final mode = Map<String, dynamic>.from(answers['mode'] as Map);
    expect(mode['answers'], <String>['Chat']);
    expect(find.text('submitted: Chat'), findsOneWidget);

    final stored = jsonDecode(StorageService.getString(requestStorageKey)!);
    expect(stored, containsPair('identity', requestIdentity));
  });

  testWidgets('ignore submits empty request user input answers', (
    tester,
  ) async {
    MethodCall? submittedCall;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(agentRuntimeChannel, (call) async {
      submittedCall = call;
      return <String, dynamic>{'ok': true};
    });

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: AgentRequestCard(cardData: _requestCardData())),
      ),
    );

    await tester.tap(find.text('Ignore'));
    await tester.pumpAndSettle();

    final arguments = Map<String, dynamic>.from(
      submittedCall!.arguments as Map,
    );
    expect(arguments['requestId'], 'request-1');
    expect(arguments['response'], {'answers': <String, dynamic>{}});
    expect(find.text('ignored'), findsOneWidget);
  });

  testWidgets('pending request ignores legacy submitted cache', (tester) async {
    await StorageService.setString(
      requestStorageKey,
      jsonEncode(<String, dynamic>{
        'status': 'submitted',
        'answers': <String>['Chat'],
      }),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: AgentRequestCard(cardData: _requestCardData())),
      ),
    );

    expect(find.text('submitted: Chat'), findsNothing);
    expect(find.text('Plan'), findsOneWidget);
    expect(find.text('Chat'), findsOneWidget);
    expect(find.text('No, tell Claude Code how to adjust'), findsOneWidget);
  });

  testWidgets('pending request restores exact submitted cache after refresh', (
    tester,
  ) async {
    await StorageService.setString(
      requestStorageKey,
      jsonEncode(<String, dynamic>{
        'identity': requestIdentity,
        'status': 'submitted',
        'answers': <String>['Chat'],
      }),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: AgentRequestCard(cardData: _requestCardData())),
      ),
    );

    expect(find.text('submitted: Chat'), findsOneWidget);
    expect(find.text('Plan'), findsNothing);
    expect(find.text('No, tell Claude Code how to adjust'), findsNothing);
  });

  testWidgets('does not render duplicate title and detail question text', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AgentRequestCard(
            cardData: _requestCardData(detail: 'Choose mode'),
          ),
        ),
      ),
    );

    expect(find.text('Choose mode'), findsOneWidget);
  });

  testWidgets('custom answer input uses provider field styling', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 420,
            child: AgentRequestCard(cardData: _requestCardData()),
          ),
        ),
      ),
    );

    final optionRow = find.byKey(const ValueKey('agent-request-option-row-1'));
    final customInput = find.byKey(
      const ValueKey('agent-request-custom-answer-input'),
    );
    final textField = tester.widget<TextField>(find.byType(TextField));

    expect(optionRow, findsOneWidget);
    expect(customInput, findsOneWidget);
    expect(textField.minLines, 1);
    expect(textField.maxLines, 1);
    expect(textField.textInputAction, TextInputAction.done);
    expect(
      textField.decoration?.labelText,
      'No, tell Claude Code how to adjust',
    );
    expect(textField.decoration?.hintText, 'Describe the adjustment');
    expect(textField.style?.fontSize, 13);
    expect(
      tester.getTopLeft(customInput).dx,
      closeTo(tester.getTopLeft(optionRow).dx, 0.1),
    );
    expect(
      tester.getSize(customInput).width,
      closeTo(tester.getSize(optionRow).width, 0.1),
    );
  });

  testWidgets(
    'custom answer input keeps keyboard clearance in scroll padding',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: MediaQuery(
              data: const MediaQueryData(
                viewInsets: EdgeInsets.only(bottom: 320),
              ),
              child: AgentRequestCard(cardData: _requestCardData()),
            ),
          ),
        ),
      );

      final textField = tester.widget<TextField>(find.byType(TextField));

      expect(textField.scrollPadding.top, 24);
      expect(textField.scrollPadding.bottom, 416);
    },
  );

  testWidgets('submits custom adjustment text when entered', (tester) async {
    MethodCall? submittedCall;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(agentRuntimeChannel, (call) async {
      submittedCall = call;
      return <String, dynamic>{'ok': true};
    });

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: AgentRequestCard(cardData: _requestCardData())),
      ),
    );

    await tester.enterText(
      find.byType(TextField),
      'Please make the options wider',
    );
    await tester.pump();
    await tester.tap(find.text('Submit ↵'));
    await tester.pumpAndSettle();

    final arguments = Map<String, dynamic>.from(
      submittedCall!.arguments as Map,
    );
    final response = Map<String, dynamic>.from(arguments['response'] as Map);
    final answers = Map<String, dynamic>.from(response['answers'] as Map);
    final mode = Map<String, dynamic>.from(answers['mode'] as Map);
    expect(mode['answers'], <String>['Please make the options wider']);
  });

  testWidgets('fills the available message width', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 360,
              child: AgentRequestCard(cardData: _requestCardData()),
            ),
          ),
        ),
      ),
    );

    final surface = find.byKey(const ValueKey('agent-request-card-surface'));
    expect(surface, findsOneWidget);
    expect(tester.getSize(surface).width, closeTo(360, 0.1));
  });
}

Map<String, dynamic> _requestCardData({String detail = 'Pick one'}) {
  return <String, dynamic>{
    'type': 'agent_request',
    'agentId': 'claude-code-acp',
    'agentName': 'Claude Code',
    'requestId': 'request-1',
    'cardId': 'request-1-card',
    'requestKind': 'user_input',
    'title': 'Choose mode',
    'detail': detail,
    'questionId': 'mode',
    'status': 'pending',
    'startTime': 1000,
    'rawParamsJson': jsonEncode({
      'questions': [
        {
          'id': 'mode',
          'question': 'Choose mode',
          'options': [
            {'label': 'Plan', 'description': 'Plan first'},
            {'label': 'Chat', 'description': 'Answer directly'},
          ],
        },
      ],
    }),
  };
}
