// lib/services/tips_service.dart

import 'package:shared_preferences/shared_preferences.dart';

const String _kTipsKey = 'user_message_tips';
const int _kMaxTipsCount = 3;

/// Saves a user message to the persistent tips.
///
/// This function adds the new message to the top of the tips,
/// ensures no duplicates are present, and trims the list to the
/// maximum size of [_kMaxTipsCount].
Future<void> saveMessageToTips(String message) async {
  if (message.trim().isEmpty) return;

  final prefs = await SharedPreferences.getInstance();
  List<String> tips = prefs.getStringList(_kTipsKey) ?? [];

  // Remove the message if it already exists to avoid duplicates and move it to the top.
  tips.remove(message);

  // Add the new message to the beginning of the list.
  tips.insert(0, message);

  // Trim the list to the maximum allowed size.
  if (tips.length > _kMaxTipsCount) {
    tips = tips.sublist(0, _kMaxTipsCount);
  }

  await prefs.setStringList(_kTipsKey, tips);
}

/// Retrieves the list of recent user messages from persistent storage.
Future<List<String>> getTipsMessages() async {
  final prefs = await SharedPreferences.getInstance();
  return prefs.getStringList(_kTipsKey) ?? [];
}
