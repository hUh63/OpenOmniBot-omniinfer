import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ui/features/home/pages/termux_setting/termux_setting_page.dart';
import 'package:ui/l10n/generated/app_localizations.dart';
import 'package:ui/services/omnibot_resource_service.dart';
import 'package:ui/services/special_permission.dart';
import 'package:ui/theme/app_theme.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const terminalChannel = MethodChannel(
    'cn.com.omnimind.bot/SpecialPermissionEvent',
  );
  const terminalEventsChannel = MethodChannel(
    'cn.com.omnimind.bot/SpecialPermissionEvents',
  );
  late Directory workspaceDirectory;
  late Completer<void> switchGate;
  late Completer<void> switchHandlerDone;
  late bool switchShouldFail;
  late bool cancelCalled;

  setUp(() async {
    workspaceDirectory = await Directory.systemTemp.createTemp(
      'omnibot-termux-setting-test-',
    );
    OmnibotResourceService.debugSetWorkspacePaths(
      OmnibotWorkspacePaths(
        rootPath: workspaceDirectory.path,
        shellRootPath: '/workspace',
        internalRootPath: '${workspaceDirectory.path}/.omnibot',
      ),
    );
    switchGate = Completer<void>();
    switchHandlerDone = Completer<void>();
    switchShouldFail = false;
    cancelCalled = false;

    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      terminalEventsChannel,
      (_) async => null,
    );
    messenger.setMockMethodCallHandler(terminalChannel, (call) async {
      switch (call.method) {
        case 'getEmbeddedTerminalDistribution':
          return 'alpine';
        case 'getEmbeddedTerminalSetupInventory':
          return <String, dynamic>{'packages': <String, dynamic>{}};
        case 'getEmbeddedTerminalAutoStartTasks':
          return <String, dynamic>{'tasks': <dynamic>[]};
        case 'switchEmbeddedTerminalDistribution':
          await switchGate.future;
          if (switchShouldFail) {
            switchHandlerDone.complete();
            throw PlatformException(code: 'DOWNLOAD_FAILED', message: '下载失败');
          }
          switchHandlerDone.complete();
          return 'ubuntu';
        case 'cancelEmbeddedTerminalInit':
          cancelCalled = true;
          return true;
      }
      return null;
    });
  });

  tearDown(() async {
    if (!switchGate.isCompleted) {
      switchGate.complete();
    }
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(terminalChannel, null);
    messenger.setMockMethodCallHandler(terminalEventsChannel, null);
    OmnibotResourceService.debugResetWorkspacePaths();
    await workspaceDirectory.delete(recursive: true);
  });

  Widget buildTestApp() {
    return MaterialApp(
      locale: const Locale('zh'),
      theme: AppTheme.lightTheme,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: const TermuxSettingPage(),
    );
  }

  Future<void> pumpTestPage(WidgetTester tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(800, 1600);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    await tester.pumpWidget(buildTestApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
  }

  testWidgets('Ubuntu 准备期间展示取消入口', (tester) async {
    await pumpTestPage(tester);

    await tester.tap(find.text('Ubuntu'));
    await tester.pump();

    var selector = tester.widget<SegmentedButton<EmbeddedTerminalDistribution>>(
      find.byType(SegmentedButton<EmbeddedTerminalDistribution>),
    );
    expect(selector.selected, <EmbeddedTerminalDistribution>{
      EmbeddedTerminalDistribution.ubuntu,
    });
    expect(find.text('取消下载'), findsOneWidget);

    await tester.tap(find.text('取消下载'));
    await tester.pump();
    expect(cancelCalled, isTrue);

    switchGate.complete();
    await tester.runAsync(() => switchHandlerDone.future);
    await tester.pump();
  });

  testWidgets('Ubuntu 准备失败时回滚原发行版', (tester) async {
    switchShouldFail = true;
    switchGate.complete();
    await pumpTestPage(tester);

    await tester.tap(find.text('Ubuntu'));
    await tester.pump();
    await tester.runAsync(() async {
      await switchHandlerDone.future;
      await Future<void>.delayed(const Duration(milliseconds: 20));
    });
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    final selector = tester
        .widget<SegmentedButton<EmbeddedTerminalDistribution>>(
          find.byType(SegmentedButton<EmbeddedTerminalDistribution>),
        );
    expect(selector.selected, <EmbeddedTerminalDistribution>{
      EmbeddedTerminalDistribution.alpine,
    });
  });
}
