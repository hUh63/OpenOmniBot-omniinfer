/// LLM聊天消息模型
class LlmMessage {
  final String id;
  final String role; // 'user' 或 'assistant'
  final String content;
  final int timestamp;

  LlmMessage({
    required this.id,
    required this.role,
    required this.content,
    required this.timestamp,
  });

  /// 从Map创建LlmMessage实例
  factory LlmMessage.fromMap(Map<String, dynamic> map) {
    return LlmMessage(
      id: map['id'] as String,
      role: map['role'] as String,
      content: map['content'] as String,
      timestamp: map['timestamp'] as int,
    );
  }

  /// 将LlmMessage转换为Map
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'role': role,
      'content': content,
      'timestamp': timestamp,
    };
  }

  @override
  String toString() {
    return 'LlmMessage(id: $id, role: $role, content: $content, timestamp: $timestamp)';
  }
}

/// LLM对话响应模型
class LlmConversationResponse {
  final String conversationId;
  final List<LlmMessage> messages;
  final int createdAt;
  final int updatedAt;

  LlmConversationResponse({
    required this.conversationId,
    required this.messages,
    required this.createdAt,
    required this.updatedAt,
  });

  /// 从Map创建LlmConversationResponse实例
  factory LlmConversationResponse.fromMap(Map<String, dynamic> map) {
    final messagesList = (map['messages'] as List<dynamic>?)
        ?.map((msg) => LlmMessage.fromMap(msg as Map<String, dynamic>))
        .toList() ?? [];

    return LlmConversationResponse(
      conversationId: map['conversation_id'] as String,
      messages: messagesList,
      createdAt: map['created_at'] as int,
      updatedAt: map['updated_at'] as int,
    );
  }

  /// 将LlmConversationResponse转换为Map
  Map<String, dynamic> toMap() {
    return {
      'conversation_id': conversationId,
      'messages': messages.map((msg) => msg.toMap()).toList(),
      'created_at': createdAt,
      'updated_at': updatedAt,
    };
  }

  @override
  String toString() {
    return 'LlmConversationResponse(conversationId: $conversationId, messages: ${messages.length} messages, createdAt: $createdAt, updatedAt: $updatedAt)';
  }
}

/// 任务可执行性检查响应模型
class TaskExecutableResponse {
  final bool isExecutable;
  final String instruction;
  final double confidence;

  TaskExecutableResponse({
    required this.isExecutable,
    required this.instruction,
    required this.confidence,
  });

  /// 从Map创建TaskExecutableResponse实例
  factory TaskExecutableResponse.fromMap(Map<String, dynamic> map) {
    return TaskExecutableResponse(
      isExecutable: map['is_executable'] as bool? ?? false,
      instruction: map['instruction'] as String? ?? '',
      confidence: (map['confidence'] as num?)?.toDouble() ?? 0.0,
    );
  }

  /// 将TaskExecutableResponse转换为Map
  Map<String, dynamic> toMap() {
    return {
      'is_executable': isExecutable,
      'instruction': instruction,
      'confidence': confidence,
    };
  }

  @override
  String toString() {
    return 'TaskExecutableResponse(isExecutable: $isExecutable, instruction: $instruction, confidence: $confidence)';
  }
}
