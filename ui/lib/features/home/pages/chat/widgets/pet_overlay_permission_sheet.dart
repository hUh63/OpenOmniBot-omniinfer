import 'package:flutter/material.dart';
import 'package:ui/features/home/pages/authorize/widgets/permission_prompt_sheet.dart';
import 'package:ui/features/home/pages/authorize/widgets/permission_section.dart';
import 'package:ui/l10n/legacy_text_localizer.dart';

/// Pet overlay permission prompt backed by the shared glass permission card.
class PetOverlayPermissionSheet extends StatelessWidget {
  const PetOverlayPermissionSheet({super.key, required this.permissions});

  final List<PermissionData> permissions;

  static Future<bool> show(
    BuildContext context, {
    required List<PermissionData> permissions,
  }) {
    return PermissionPromptSheet.show(
      context,
      permissions: permissions,
      actionLabel: LegacyTextLocalizer.localize('唤起宠物'),
      actionKey: const ValueKey('pet-overlay-permission-continue-button'),
    );
  }

  @override
  Widget build(BuildContext context) {
    return PermissionPromptSheet(
      permissions: permissions,
      actionLabel: LegacyTextLocalizer.localize('唤起宠物'),
      actionKey: const ValueKey('pet-overlay-permission-continue-button'),
    );
  }
}
