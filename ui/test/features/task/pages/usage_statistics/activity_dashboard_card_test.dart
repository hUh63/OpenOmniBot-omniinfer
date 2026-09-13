import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/features/task/pages/usage_statistics/widgets/activity_dashboard_card.dart';
import 'package:ui/l10n/legacy_text_localizer.dart';
import 'package:ui/services/storage_service.dart';

void main() {
  const assistCoreChannel = MethodChannel(
    'cn.com.omnimind.bot/AssistCoreEvent',
  );

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    await StorageService.init();
    LegacyTextLocalizer.setResolvedLocale(const Locale('zh'));
  });

  tearDown(() {
    LegacyTextLocalizer.clearResolvedLocale();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(assistCoreChannel, null);
  });

  testWidgets('shows conversation, per-model token, and cache statistics', (
    tester,
  ) async {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final todayMs = today.add(const Duration(hours: 12)).millisecondsSinceEpoch;
    final yesterdayMs = today
        .subtract(const Duration(days: 1))
        .add(const Duration(hours: 12))
        .millisecondsSinceEpoch;

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(assistCoreChannel, (call) async {
          switch (call.method) {
            case 'getConversations':
              return <Map<String, Object?>>[
                _conversation(id: 1, createdAt: todayMs),
                _conversation(id: 2, createdAt: yesterdayMs),
              ];
            case 'getTokenUsageRecords':
              return <Map<String, Object?>>[
                {
                  'id': 1,
                  'conversationId': 1,
                  'model': 'openai/gpt-4o',
                  'promptTokens': 1200,
                  'completionTokens': 300,
                  'reasoningTokens': 100,
                  'textTokens': 200,
                  'cachedTokens': 400,
                  'createdAt': todayMs,
                },
                {
                  'id': 2,
                  'conversationId': 2,
                  'model': 'qwen/qwen3',
                  'promptTokens': 800,
                  'completionTokens': 600,
                  'reasoningTokens': 0,
                  'textTokens': 0,
                  'cachedTokens': 0,
                  'createdAt': yesterdayMs,
                },
              ];
            default:
              return null;
          }
        });

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 375,
            child: ActivityDashboardCard(weeksToShow: 4),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('900'), findsOneWidget);
    expect(find.text('tokens'), findsOneWidget);
    expect(find.text('400'), findsOneWidget);
    expect(find.text('缓存'), findsOneWidget);
    final content = tester.widget<Padding>(
      find.byKey(const ValueKey('usage-statistics-content')),
    );
    expect(content.padding, const EdgeInsets.symmetric(horizontal: 20));
    expect(content.child, isA<FadeTransition>());

    await tester.tap(find.text('Token'));
    await tester.pumpAndSettle();

    expect(find.text('qwen3'), findsOneWidget);
    expect(find.text('gpt-4o'), findsOneWidget);
    expect(find.text('67%'), findsOneWidget);
    expect(find.text('33%'), findsOneWidget);
    expect(find.text('openai/gpt-4o'), findsNothing);
  });
}

Map<String, Object?> _conversation({required int id, required int createdAt}) {
  return <String, Object?>{
    'id': id,
    'mode': 'normal',
    'title': 'Conversation $id',
    'status': 0,
    'messageCount': 1,
    'createdAt': createdAt,
    'updatedAt': createdAt,
  };
}
