import 'package:ui/models/chat_message_model.dart';

class ChatService {
  static List<ChatMessageModel> getRecentMessages(
    List<ChatMessageModel> messages, {
    int maxCount = 10,
  }) {
    final errorMessageIds = messages
        .where((msg) => msg.isError)
        .map((msg) => msg.id.replaceAll('-ai', '-user'))
        .toSet();

    // 获取最近的对话记录，过滤掉：
    // 1. 卡片消息（type=2）
    // 2. 错误消息（isError=true）
    // 3. 错误消息对应的用户消息
    final textMessages = messages.where((msg) {
      if (msg.type != 1) return false; // 过滤掉卡片消息
      if (msg.isError) return false; // 过滤掉错误消息
      if (errorMessageIds.contains(msg.id)) return false; // 过滤掉错误消息对应的用户消息
      return true;
    }).toList();

    // 取最近的N条消息
    return textMessages.length > maxCount
        ? textMessages.sublist(0, maxCount)
        : textMessages;
  }
}

