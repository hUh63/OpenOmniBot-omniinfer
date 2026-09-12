import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:ui/services/special_permission.dart';
import 'package:ui/theme/theme_context.dart';

import '../onboarding_environment_controller.dart';
import '../onboarding_l10n.dart';
import '../widgets/onboarding_entrance.dart';
import '../widgets/onboarding_option_row.dart';
import '../widgets/onboarding_page_scaffold.dart';

/// Step 1: pick the local Linux distribution. Intentionally not scrollable —
/// everything fits on one screen.
class OnboardingSystemPage extends StatelessWidget {
  const OnboardingSystemPage({super.key, required this.controller});

  final OnboardingEnvironmentController controller;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return LayoutBuilder(
      key: const ValueKey('tutorial-system-page'),
      builder: (context, constraints) {
        final compactHeight = constraints.maxHeight < 700;
        return Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 760),
            child: Padding(
              padding: EdgeInsets.fromLTRB(
                20,
                20,
                20,
                compactHeight ? 14 : 24,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  OnboardingPageHeading(
                    icon: LucideIcons.box,
                    title: onbTr(
                      context,
                      '选择本地 Linux 系统',
                      'Choose your local Linux system',
                    ),
                  ),
                  const SizedBox(height: 10),
                  OnboardingEntrance(
                    index: 0,
                    child: Text(
                      onbTr(
                        context,
                        '用于 Agent 执行命令和管理项目文件；系统与工作区只保留在本机。',
                        'Used for agent commands and project files. The system and workspace stay on-device.',
                      ),
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: palette.textSecondary,
                        height: 1.45,
                      ),
                    ),
                  ),
                  SizedBox(height: compactHeight ? 12 : 18),
                  OnboardingEntrance(
                    index: 1,
                    child: _DistributionRow(
                      controller: controller,
                      distribution: EmbeddedTerminalDistribution.alpine,
                      asset: 'assets/welcome/distro_alpine.svg',
                      title: 'Alpine',
                      badge: onbTr(context, '轻量推荐', 'Lightweight'),
                      description: onbTr(
                        context,
                        '启动快、占用小，适合移动设备和大多数 Agent 工作。',
                        'Fast and compact for mobile devices and most agent tasks.',
                      ),
                      detail: onbTr(
                        context,
                        'apk · 更省空间',
                        'apk · Smaller footprint',
                      ),
                    ),
                  ),
                  const SizedBox(height: 2),
                  OnboardingEntrance(
                    index: 2,
                    child: _DistributionRow(
                      controller: controller,
                      distribution: EmbeddedTerminalDistribution.ubuntu,
                      asset: 'assets/welcome/distro_ubuntu.svg',
                      title: 'Ubuntu',
                      badge: onbTr(context, '熟悉生态', 'Familiar'),
                      description: onbTr(
                        context,
                        '使用 apt，更接近常见服务器环境，软件生态更丰富。',
                        'Uses apt and closely matches common server environments.',
                      ),
                      detail: 'Ubuntu Base 24.04 · apt',
                    ),
                  ),
                  const Spacer(),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _DistributionRow extends StatelessWidget {
  const _DistributionRow({
    required this.controller,
    required this.distribution,
    required this.asset,
    required this.title,
    required this.badge,
    required this.description,
    required this.detail,
  });

  final OnboardingEnvironmentController controller;
  final EmbeddedTerminalDistribution distribution;
  final String asset;
  final String title;
  final String badge;
  final String description;
  final String detail;

  @override
  Widget build(BuildContext context) {
    final selected = controller.distribution == distribution;
    final id = distribution == EmbeddedTerminalDistribution.alpine
        ? 'alpine'
        : 'ubuntu';
    return OnboardingOptionRow(
      tapKey: ValueKey<String>('tutorial-distribution-$id'),
      leading: _DistroIcon(asset: asset),
      title: title,
      badge: badge,
      description: description,
      detail: detail,
      selected: selected,
      onTap: controller.isBusy
          ? null
          : () => controller.selectDistribution(distribution),
    );
  }
}

/// Brand SVG logo in a neutral rounded surface with a constant hairline
/// border — selection is conveyed by the surrounding option row, not here.
class _DistroIcon extends StatelessWidget {
  const _DistroIcon({required this.asset});

  final String asset;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Container(
      width: 40,
      height: 40,
      decoration: BoxDecoration(
        color: palette.surfaceSecondary,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: palette.borderSubtle),
      ),
      alignment: Alignment.center,
      child: SvgPicture.asset(asset, width: 22, height: 22),
    );
  }
}
