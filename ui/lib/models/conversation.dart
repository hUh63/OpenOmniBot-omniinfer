/// 会话模型
class Conversation {
  final int conversationId;
  final String title;
  final int createdAt;
  final int updatedAt;

  Conversation({
    required this.conversationId,
    required this.title,
    required this.createdAt,
    required this.updatedAt,
  });

  /// 从Map创建Conversation实例
  factory Conversation.fromMap(Map<dynamic, dynamic> map) {
    return Conversation(
      conversationId: map['conversationId'] as int,
      title: map['title'] as String,
      createdAt: map['createdAt'] as int? ?? 0,
      updatedAt: map['updatedAt'] as int? ?? 0,
    );
  }

  /// 将Conversation转换为Map
  Map<String, dynamic> toMap() {
    return {
      'conversationId': conversationId,
      'title': title,
      'createdAt': createdAt,
      'updatedAt': updatedAt,
    };
  }

  @override
  String toString() {
    return 'Conversation(conversationId: $conversationId, title: $title, createdAt: $createdAt, updatedAt: $updatedAt)';
  }
}