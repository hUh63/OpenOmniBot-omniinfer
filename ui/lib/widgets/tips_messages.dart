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

    return Container(
      height: 38, // Provides a fixed height for the horizontal list
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
              ),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceVariant.withOpacity(0.7),
                borderRadius: BorderRadius.circular(5.0),
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
                    fontSize: 12,
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
