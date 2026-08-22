/// 消息模型
class Message {
  final int id;
  final String messageId;
  final int type;
  final int user;
  final String content;
  final int createdAt;
  final int updatedAt;

  Message({
    required this.id,
    required this.messageId,
    required this.type,
    required this.user,
    required this.content,
    required this.createdAt,
    required this.updatedAt,
  });

  /// 从Map创建Message实例
  factory Message.fromMap(Map<dynamic, dynamic> map) {
    return Message(
      id: map['id'] as int? ?? 0,
      messageId: map['messageId'] as String? ?? '',
      type: map['type'] as int? ?? 1,
      user: map['user'] as int? ?? 2,
      content: map['content'] as String,
      createdAt: map['createdAt'] as int? ?? 0,
      updatedAt: map['updatedAt'] as int? ?? 0,
    );
  }

  /// 将Message转换为Map
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'messageId': messageId,
      'type': type,
      'user': user,
      'content': content,
      'createdAt': createdAt,
      'updatedAt': updatedAt,
    };
  }

  @override
  String toString() {
    return 'Message(id: $id, messageId: $messageId, type: $type, user: $user, content: $content, createdAt: $createdAt, updatedAt: $updatedAt)';
  }
}