import 'package:flutter_test/flutter_test.dart';
import 'package:ui/features/home/pages/command_overlay/services/manual_recording_permission_guard.dart';
import 'package:ui/services/special_permission.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(spePermission, null);
  });

  test('requires both accessibility and overlay permissions', () async {
    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(spePermission, (call) async {
          calls.add(call.method);
          return call.method == 'isAndroidGuiAccessibilityReady';
        });

    final check = await ManualRecordingPermissionGuard.check();

    expect(check.accessibilityReady, isTrue);
    expect(check.overlayGranted, isFalse);
    expect(check.isAuthorized, isFalse);
    expect(calls, <String>[
      'isAndroidGuiAccessibilityReady',
      'isOverlayPermission',
    ]);
  });
}
