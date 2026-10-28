// lib/widgets/tips_messages.dart

import 'package:flutter/material.dart';

/// A widget that displays a horizontal list of recent messages as clickable chips.
class TipsMessages extends StatelessWidget {
  final List<String> messages;
  final Function(String) onMessageTap;

  const TipsMessages({
    super.key,
    required this.messages,
    required this.onMessageTap,
  });

  /// Truncates a long message for display purposes.
  ///
  /// If the message is longer than a defined maximum length, it shows
  /// the beginning and end of the string, separated by an ellipsis.
  String _truncateMessage(String message) {
    const int maxLength = 25;
    if (message.length <= maxLength) {
      return message;
    }

    final int startLength = 12;
    final int endLength = 8;

    return '${message.substring(0, startLength)}...${message.substring(message.length - endLength)}';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    // 固定 52 在无障碍大字体（1.5~2.0x）下会把 chip 文字裁掉（ListView 硬裁剪，
    // 连 RenderFlex 告警都没有）。按 textScaler 放大，并设上限避免顶掉输入区。
    final textScale = MediaQuery.textScalerOf(context).scale(1);
    final rowHeight = (52 * (textScale < 1 ? 1 : textScale)).clamp(52.0, 96.0);
    return Container(
      height: rowHeight,
      padding: const EdgeInsets.only(top: 4, bottom: 8, left: 16, right: 16),
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: messages.length,
        itemBuilder: (context, index) {
          final message = messages[index];
          return GestureDetector(
            // The `onTap` callback uses the original, full-length message.
            onTap: () => onMessageTap(message),
            child: Container(
              margin: const EdgeInsets.only(right: 8.0),
              padding: const EdgeInsets.symmetric(
                horizontal: 14.0,
                vertical: 8.0,
              ),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceVariant.withOpacity(0.7),
                borderRadius: BorderRadius.circular(20.0),
                border: Border.all(
                  color: theme.colorScheme.surfaceVariant,
                  width: 1,
                ),
              ),
              child: Center(
                child: Text(
                  // The displayed text is the truncated version.
                  _truncateMessage(message),
                  style: TextStyle(
                    color: theme.colorScheme.onSurface.withOpacity(0.8),
                    fontWeight: FontWeight.w500,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
