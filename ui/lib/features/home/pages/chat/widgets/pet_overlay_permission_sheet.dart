import 'dart:async';

import 'package:flutter/material.dart';
import 'package:ui/features/home/pages/authorize/widgets/permission_section.dart';
import 'package:ui/l10n/legacy_text_localizer.dart';
import 'package:ui/services/permission_service.dart';
import 'package:ui/theme/app_colors.dart';
import 'package:ui/theme/theme_context.dart';
import 'package:ui/widgets/omni_glass.dart';

/// 宠物悬浮窗权限卡片。
///
/// 保留原来的玻璃权限卡片样式，但这里只检查悬浮窗权限，
/// 授权完成后的唯一动作是继续唤起宠物。
class PetOverlayPermissionSheet extends StatefulWidget {
  const PetOverlayPermissionSheet({super.key, required this.permission});

  final PermissionData permission;

  static Future<bool> show(
    BuildContext context, {
    required PermissionData permission,
  }) async {
    return await showModalBottomSheet<bool>(
          context: context,
          isScrollControlled: true,
          backgroundColor: Colors.transparent,
          barrierColor: Colors.black.withValues(alpha: 0.18),
          builder: (context) =>
              PetOverlayPermissionSheet(permission: permission),
        ) ??
        false;
  }

  @override
  State<PetOverlayPermissionSheet> createState() =>
      _PetOverlayPermissionSheetState();
}

class _PetOverlayPermissionSheetState extends State<PetOverlayPermissionSheet>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_checkPermission());
    }
  }

  Future<void> _checkPermission() async {
    await PermissionService.checkPermissions(<PermissionData>[
      widget.permission,
    ]);
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final isDark = context.isDarkTheme;

    return Padding(
      padding: EdgeInsets.fromLTRB(
        12,
        0,
        12,
        12 + MediaQuery.of(context).viewInsets.bottom,
      ),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.75,
        ),
        child: OmniGlassPanel(
          borderRadius: BorderRadius.circular(22),
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 32),
              Text(
                LegacyTextLocalizer.isEnglish
                    ? 'Please check the permission below'
                    : '请检查下列权限',
                style: TextStyle(
                  color: isDark ? palette.textPrimary : AppColors.text,
                  fontSize: 20,
                  fontWeight: FontWeight.w600,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 26),
              PermissionSection(
                permissions: <PermissionData>[widget.permission],
                spacing: 36,
                onPermissionChanged: () {
                  unawaited(_checkPermission());
                },
              ),
              const SizedBox(height: 32),
              Center(
                child: ValueListenableBuilder<bool>(
                  valueListenable: widget.permission.notifier,
                  builder: (context, authorized, child) {
                    return GestureDetector(
                      key: const ValueKey(
                        'pet-overlay-permission-continue-button',
                      ),
                      onTap: authorized
                          ? () => Navigator.of(context).pop(true)
                          : null,
                      child: Opacity(
                        opacity: authorized ? 1 : 0.5,
                        child: Container(
                          width: double.infinity,
                          constraints: const BoxConstraints(maxWidth: 288),
                          height: 48,
                          decoration: BoxDecoration(
                            gradient: isDark
                                ? LinearGradient(
                                    begin: const Alignment(0.14, -1.09),
                                    end: const Alignment(1.10, 1.26),
                                    colors: [
                                      Color.lerp(
                                        palette.surfaceElevated,
                                        palette.accentPrimary,
                                        0.18,
                                      )!,
                                      Color.lerp(
                                        palette.surfaceSecondary,
                                        palette.accentPrimary,
                                        0.34,
                                      )!,
                                    ],
                                  )
                                : const LinearGradient(
                                    begin: Alignment(0.14, -1.09),
                                    end: Alignment(1.10, 1.26),
                                    colors: [
                                      Color(0xFF1930D9),
                                      Color(0xFF2CA5F0),
                                    ],
                                  ),
                            borderRadius: BorderRadius.circular(12),
                            border: isDark
                                ? Border.all(color: palette.borderSubtle)
                                : null,
                          ),
                          child: Center(
                            child: Text(
                              LegacyTextLocalizer.localize('唤起宠物'),
                              style: TextStyle(
                                color: isDark
                                    ? palette.textPrimary
                                    : Colors.white,
                                fontSize: 16,
                                fontFamily: 'PingFang SC',
                                fontWeight: FontWeight.w600,
                                height: 1.5,
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),
              const SizedBox(height: 28),
            ],
          ),
        ),
      ),
    );
  }
}
