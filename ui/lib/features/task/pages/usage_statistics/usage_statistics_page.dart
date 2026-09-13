import 'package:flutter/material.dart';
import 'package:ui/features/task/pages/usage_statistics/widgets/activity_dashboard_card.dart';
import 'package:ui/l10n/legacy_text_localizer.dart';
import 'package:ui/theme/theme_context.dart';
import 'package:ui/widgets/common_app_bar.dart';

/// Conversation activity and model token usage statistics.
///
/// This page intentionally depends only on conversation and token-usage data.
/// It remains independent from the legacy task-execution history feature.
class UsageStatisticsPage extends StatelessWidget {
  const UsageStatisticsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Scaffold(
      backgroundColor: palette.pageBackground,
      appBar: CommonAppBar(
        title: LegacyTextLocalizer.localize('轨迹'),
        primary: true,
      ),
      body: SafeArea(
        top: false,
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 720),
            child: const SingleChildScrollView(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: ActivityDashboardCard(),
            ),
          ),
        ),
      ),
    );
  }
}
