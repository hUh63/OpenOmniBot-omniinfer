import 'dart:convert';
import '../models/conversation.dart';
import '../models/message.dart';
import '../models/paged_messages_result.dart';

/// JSON工具类，用于将模型对象转换为JSON字符串
class JsonUtil {
  /// 将Conversation对象转换为JSON字符串
  static String conversationToJson(Conversation conversation) {
    return jsonEncode(conversation.toMap());
  }

  /// 将Message对象转换为JSON字符串
  static String messageToJson(Message message) {
    return jsonEncode(message.toMap());
  }

  /// 将PagedMessagesResult对象转换为JSON字符串
  static String pagedMessagesResultToJson(PagedMessagesResult result) {
    return jsonEncode(result.toMap());
  }

  /// 将Conversation列表转换为JSON字符串
  static String conversationListToJson(List<Conversation> conversations) {
    final List<Map<String, dynamic>> list = conversations.map((c) => c.toMap()).toList();
    return jsonEncode(list);
  }

  /// 将Message列表转换为JSON字符串
  static String messageListToJson(List<Message> messages) {
    final List<Map<String, dynamic>> list = messages.map((m) => m.toMap()).toList();
    return jsonEncode(list);
  }

  /// 将Map转换为JSON字符串
  static String mapToJson(Map<String, dynamic> map) {
    return jsonEncode(map);
  }
}