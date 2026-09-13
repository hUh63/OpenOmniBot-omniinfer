import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:ui/theme/theme_context.dart';

import '../onboarding_definitions.dart';
import '../onboarding_environment_controller.dart';
import '../onboarding_l10n.dart';
import '../widgets/onboarding_page_scaffold.dart';

/// Step 3: pick optional agents and connection tools, then review the setup.
class OnboardingToolsPage extends StatelessWidget {
  const OnboardingToolsPage({
    super.key,
    required this.controller,
    required this.scrollController,
  });

  final OnboardingEnvironmentController controller;
  final ScrollController scrollController;

  @override
  Widget build(BuildContext context) {
    return OnboardingPageScaffold(
      icon: LucideIcons.packagePlus,
      title: onbTr(
        context,
        '配置 Agent 与连接工具',
        'Configure agents and connections',
      ),
      description: onbTr(
        context,
        '可选安装 Codex、Claude Code、OpenCode 或 SSH；账号登录可在安装完成后进行。',
        'Optionally install Codex, Claude Code, OpenCode, or SSH. Sign in after installation.',
      ),
      scrollController: scrollController,
      children: [
        _OptionalCapabilitySummary(),
        const SizedBox(height: 24),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: optionalTools
              .map(
                (tool) => _OptionalToolChip(tool: tool, controller: controller),
              )
              .toList(growable: false),
        ),
        const SizedBox(height: 26),
        _SetupSummary(controller: controller),
      ],
    );
  }
}

class _OptionalCapabilitySummary extends StatelessWidget {
  const _OptionalCapabilitySummary();

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(LucideIcons.blocks, size: 18, color: palette.accentPrimary),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            onbTr(
              context,
              '基础配置完成后，可从插件市场按需安装 GUI 操作和 Vibe Builder；RunLog、复用指令、Memory 与 Skills 都可稍后使用，不影响先开始聊天。',
              'After setup, install GUI automation and Vibe Builder from the plugin market when needed. RunLog, reusable functions, Memory, and Skills remain available later and never block starting chat.',
            ),
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: palette.textSecondary,
              height: 1.55,
            ),
          ),
        ),
      ],
    );
  }
}

class _OptionalToolChip extends StatelessWidget {
  const _OptionalToolChip({required this.tool, required this.controller});

  final OptionalTool tool;
  final OnboardingEnvironmentController controller;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final selected = controller.optionalToolIds.contains(tool.id);
    return Semantics(
      button: true,
      selected: selected,
      label:
          '${tool.label}, ${onbTr(context, tool.descriptionZh, tool.descriptionEn)}',
      child: FilterChip(
        key: ValueKey<String>('tutorial-tool-${tool.id}'),
        selected: selected,
        onSelected: controller.isBusy
            ? null
            : (_) => controller.toggleOptionalTool(tool.id),
        avatar: Icon(
          tool.icon,
          size: 16,
          color: selected
              ? Theme.of(context).colorScheme.onPrimary
              : palette.textSecondary,
        ),
        label: Text(tool.label),
        tooltip: onbTr(context, tool.descriptionZh, tool.descriptionEn),
        showCheckmark: false,
        selectedColor: palette.accentPrimary,
        backgroundColor: palette.surfacePrimary,
        side: BorderSide(
          color: selected ? palette.accentPrimary : palette.borderSubtle,
        ),
        labelStyle: Theme.of(context).textTheme.labelMedium?.copyWith(
          color: selected
              ? Theme.of(context).colorScheme.onPrimary
              : palette.textPrimary,
          fontWeight: FontWeight.w600,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 9),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
      ),
    );
  }
}

/// Flat summary of what the setup will install: icon rows separated by
/// hairlines instead of a bordered box.
class _SetupSummary extends StatelessWidget {
  const _SetupSummary({required this.controller});

  final OnboardingEnvironmentController controller;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final preset = controller.selectedPreset;
    final extras = controller.selectedToolLabels;

    Widget summaryRow(IconData icon, String value) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 1),
            child: Icon(icon, size: 16, color: palette.accentPrimary),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              value,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: palette.textSecondary,
                height: 1.5,
              ),
            ),
          ),
        ],
      );
    }

    Widget hairline() => Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Divider(height: 1, thickness: 1, color: palette.borderSubtle),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              LucideIcons.listChecks,
              size: 17,
              color: palette.accentPrimary,
            ),
            const SizedBox(width: 8),
            Text(
              onbTr(context, '将要配置', 'Setup summary'),
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                color: palette.textPrimary,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
        const SizedBox(height: 14),
        summaryRow(
          LucideIcons.server,
          '${controller.distributionName} · ${onbTr(context, preset.titleZh, preset.titleEn)}',
        ),
        hairline(),
        summaryRow(LucideIcons.codeXml, preset.contents),
        if (extras.isNotEmpty) ...[
          hairline(),
          summaryRow(
            LucideIcons.packagePlus,
            '${onbTr(context, 'Agent / 工具', 'Agents / tools')}: $extras',
          ),
        ],
      ],
    );
  }
}
