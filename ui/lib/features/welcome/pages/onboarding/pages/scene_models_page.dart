import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:ui/theme/theme_context.dart';

import '../onboarding_definitions.dart';
import '../onboarding_l10n.dart';
import '../onboarding_provider_controller.dart';
import '../widgets/onboarding_common.dart';
import '../widgets/onboarding_page_scaffold.dart';

/// Steps 7-8: bind models to background roles. Used for both the primary
/// scenes page and the memory scenes page.
class OnboardingSceneModelsPage extends StatelessWidget {
  const OnboardingSceneModelsPage({
    super.key,
    required this.controller,
    required this.scrollController,
    required this.icon,
    required this.title,
    required this.description,
    required this.scenes,
    this.showError = false,
  });

  final OnboardingProviderController controller;
  final ScrollController scrollController;
  final IconData icon;
  final String title;
  final String description;
  final List<SceneDefinition> scenes;

  /// Only the last scenes page shows the inline error (save happens there).
  final bool showError;

  @override
  Widget build(BuildContext context) {
    return OnboardingPageScaffold(
      icon: icon,
      title: title,
      description: description,
      scrollController: scrollController,
      children: [
        for (var i = 0; i < scenes.length; i++) ...[
          if (i > 0) ...[
            const SizedBox(height: 18),
            Divider(
              height: 1,
              thickness: 1,
              color: context.omniPalette.borderSubtle,
            ),
            const SizedBox(height: 18),
          ],
          _SceneModelBlock(scene: scenes[i], controller: controller),
        ],
        if (showError && controller.error != null) ...[
          const SizedBox(height: 16),
          OnboardingInlineError(message: controller.error!),
        ],
      ],
    );
  }
}

class _SceneModelBlock extends StatelessWidget {
  const _SceneModelBlock({required this.scene, required this.controller});

  final SceneDefinition scene;
  final OnboardingProviderController controller;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final selectedModel = controller.sceneModelSelections[scene.id];
    final modelOptions = controller.modelOptions;
    final validValue =
        selectedModel != null &&
            modelOptions.any((model) => model.id == selectedModel)
        ? selectedModel
        : null;
    final saving = controller.savingSceneIds.contains(scene.id);

    return Column(
      key: ValueKey<String>('tutorial-scene-${scene.id}'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: palette.accentPrimary.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(12),
              ),
              alignment: Alignment.center,
              child: saving
                  ? SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: palette.accentPrimary,
                      ),
                    )
                  : Icon(scene.icon, size: 19, color: palette.accentPrimary),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    scene.title,
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: palette.textPrimary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    onbTr(context, scene.descriptionZh, scene.descriptionEn),
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: palette.textSecondary,
                      height: 1.5,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 14),
        DropdownButtonFormField<String>(
          key: ValueKey<String>(
            '${scene.id}-${controller.activeProfile?.id ?? 'none'}-${modelOptions.length}',
          ),
          initialValue: validValue,
          isExpanded: true,
          items: modelOptions
              .map(
                (model) => DropdownMenuItem<String>(
                  value: model.id,
                  child: Text(
                    model.displayName.trim().isEmpty
                        ? model.id
                        : '${model.displayName}  ·  ${model.id}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              )
              .toList(growable: false),
          onChanged: controller.sceneModelsSaving || modelOptions.isEmpty
              ? null
              : (value) {
                  if (value == null) return;
                  controller.selectSceneModel(scene.id, value);
                },
          decoration: onboardingInputDecoration(
            context,
            label: onbTr(context, '此场景使用', 'Model for this role'),
            hint: modelOptions.isEmpty
                ? onbTr(context, '请先连接并添加模型', 'Connect and add a model first')
                : onbTr(context, '选择模型', 'Choose a model'),
            icon: LucideIcons.cpu,
          ),
          dropdownColor: palette.surfacePrimary,
          style: Theme.of(
            context,
          ).textTheme.bodyMedium?.copyWith(color: palette.textPrimary),
        ),
      ],
    );
  }
}
