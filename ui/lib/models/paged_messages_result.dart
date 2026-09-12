import 'message.dart';

/// 分页消息结果模型
class PagedMessagesResult {
  final List<Message> messages;
  final bool hasNextPage;

  PagedMessagesResult({
    required this.messages,
    required this.hasNextPage,
  });

  /// 从Map创建PagedMessagesResult实例
  factory PagedMessagesResult.fromMap(Map<dynamic, dynamic> map) {
    final List<dynamic> messagesList = map['messageList'] as List<dynamic>;
    final List<Message> messages = messagesList
        .map((messageMap) => Message.fromMap(messageMap as Map<dynamic, dynamic>))
        .toList();

    return PagedMessagesResult(
      messages: messages,
      hasNextPage: map['hasMore'] as bool,
    );
  }

  /// 将PagedMessagesResult转换为Map
  Map<String, dynamic> toMap() {
    return {
      'messages': messages.map((message) => message.toMap()).toList(),
      'hasNextPage': hasNextPage,
    };
  }

  @override
  String toString() {
    return 'PagedMessagesResult(messages: $messages, hasNextPage: $hasNextPage)';
  }
}