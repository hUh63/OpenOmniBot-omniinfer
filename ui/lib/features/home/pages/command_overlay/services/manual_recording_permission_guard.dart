import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ui/core/router/go_router_manager.dart';
import 'package:ui/features/home/pages/authorize/accessibility_permission_prompt.dart';
import 'package:ui/features/home/pages/authorize/authorize_page_args.dart';
import 'package:ui/services/special_permission.dart';

class ManualRecordingPermissionCheck {
  const ManualRecordingPermissionCheck({
    required this.accessibilityReady,
    required this.overlayGranted,
  });

  final bool accessibilityReady;
  final bool overlayGranted;

  bool get isAuthorized => accessibilityReady && overlayGranted;
}

class ManualRecordingPermissionGuard {
  const ManualRecordingPermissionGuard._();

  static Future<ManualRecordingPermissionCheck> check() async {
    final accessibilityReady = await _checkPermission(
      'isAndroidGuiAccessibilityReady',
    );
    final overlayGranted = await _checkPermission('isOverlayPermission');
    return ManualRecordingPermissionCheck(
      accessibilityReady: accessibilityReady,
      overlayGranted: overlayGranted,
    );
  }

  static Future<bool> ensureAuthorized(BuildContext context) async {
    var permissionCheck = await check();
    if (permissionCheck.isAuthorized) return true;
    if (!context.mounted) return false;

    if (!permissionCheck.accessibilityReady) {
      final accessibilityReady = await showAccessibilityPermissionPrompt(
        context,
      );
      if (!accessibilityReady || !context.mounted) return false;
      permissionCheck = await check();
      if (permissionCheck.isAuthorized) return true;
    }

    if (!permissionCheck.overlayGranted) {
      final overlayGranted = await GoRouterManager.pushForResult<bool>(
        '/home/authorize',
        extra: const AuthorizePageArgs(
          requiredPermissionIds: <String>[kOverlayPermissionId],
        ),
      );
      if (overlayGranted != true || !context.mounted) return false;
    }
    return (await check()).isAuthorized;
  }

  static Future<bool> _checkPermission(String method) async {
    try {
      return await spePermission.invokeMethod<bool>(method) ?? false;
    } on PlatformException {
      return false;
    }
  }
}
