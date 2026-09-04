import 'package:flutter/material.dart';
import 'package:ui/theme/theme_context.dart';

class ContextCompactionMarkerCard extends StatelessWidget {
  const ContextCompactionMarkerCard({super.key, required this.cardData});

  final Map<String, dynamic> cardData;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final status = (cardData['status'] ?? 'completed').toString().trim();
    final label = (cardData['label'] ?? '').toString().trim().isEmpty
        ? _fallbackLabel(status)
        : (cardData['label'] ?? '').toString().trim();
    final color = switch (status) {
      'compressing' => const Color(0xFF2C7FEB),
      'failed' => const Color(0xFFE45D5D),
      'noop' => palette.textSecondary,
      _ =>
        context.isDarkTheme ? palette.accentPrimary : const Color(0xFF2F9D62),
    };
    final lineColor = color.withValues(alpha: context.isDarkTheme ? 0.5 : 0.28);
    final chipBackground = color.withValues(
      alpha: context.isDarkTheme ? 0.16 : 0.1,
    );

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      child: Row(
        children: [
          Expanded(child: Container(height: 1, color: lineColor)),
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 12),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: chipBackground,
              borderRadius: BorderRadius.circular(999),
              border: Border.all(color: lineColor),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (status == 'compressing') ...[
                  SizedBox(
                    width: 12,
                    height: 12,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(color),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: color,
                    letterSpacing: 0.1,
                  ),
                ),
              ],
            ),
          ),
          Expanded(child: Container(height: 1, color: lineColor)),
        ],
      ),
    );
  }

  String _fallbackLabel(String status) {
    return switch (status) {
      'compressing' => '正在压缩',
      'noop' => '无需压缩',
      'failed' => '压缩失败',
      _ => '已压缩',
    };
  }
}
