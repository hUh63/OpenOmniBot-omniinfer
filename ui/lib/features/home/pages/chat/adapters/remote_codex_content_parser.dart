part of '../chat_page.dart';

class _AgentUserMessageContent {
  const _AgentUserMessageContent({
    required this.text,
    required this.attachments,
  });

  final String text;
  final List<Map<String, dynamic>> attachments;
}

_AgentUserMessageContent _remoteCodexExtractUserMessageContent(dynamic value) {
  final text = StringBuffer();
  final attachments = <Map<String, dynamic>>[];

  void visit(dynamic node) {
    if (node == null) return;
    if (node is String) {
      text.write(node);
      return;
    }
    if (node is num || node is bool) {
      text.write(node);
      return;
    }
    if (node is List) {
      for (final child in node) {
        visit(child);
      }
      return;
    }

    final map = _asAgentMap(node);
    if (map == null) {
      final fallback = node.toString();
      if (fallback.isNotEmpty) {
        text.write(fallback);
      }
      return;
    }

    final type = _asAgentString(map['type'])?.toLowerCase();
    if (_remoteCodexBlockTypeLooksText(type)) {
      final blockText = _remoteCodexExtractText(
        map['text'] ?? map['content'] ?? map['value'] ?? map['input'],
      );
      if (blockText.isNotEmpty) {
        text.write(blockText);
      }
      return;
    }

    if (_remoteCodexMapLooksLikeImageBlock(map)) {
      final attachment = _remoteCodexImageAttachmentFromBlock(
        map,
        attachments.length,
      );
      if (attachment != null) {
        attachments.add(attachment);
      }
      return;
    }

    for (final key in const <String>[
      'text',
      'content',
      'message',
      'input',
      'value',
      'delta',
      'summary',
      'text_elements',
      'parts',
      'attachments',
      'images',
    ]) {
      if (!map.containsKey(key)) continue;
      final beforeTextLength = text.length;
      final beforeAttachmentLength = attachments.length;
      visit(map[key]);
      if (text.length != beforeTextLength ||
          attachments.length != beforeAttachmentLength) {
        return;
      }
    }

    final fallback = _remoteCodexExtractText(map);
    if (fallback.isNotEmpty) {
      text.write(fallback);
    }
  }

  visit(value);
  return _AgentUserMessageContent(
    text: text.toString(),
    attachments: List<Map<String, dynamic>>.unmodifiable(attachments),
  );
}

bool _remoteCodexBlockTypeLooksText(String? type) {
  if (type == null) return false;
  final normalized = type.replaceAll('-', '_');
  return normalized == 'text' ||
      normalized == 'input_text' ||
      normalized == 'message_text';
}

bool _remoteCodexBlockTypeLooksImage(String? type) {
  if (type == null) return false;
  final normalized = type.replaceAll('-', '_');
  return normalized == 'image' ||
      normalized == 'input_image' ||
      normalized == 'image_url' ||
      normalized == 'screenshot' ||
      normalized.endsWith('_image');
}

bool _remoteCodexMapLooksLikeImageBlock(Map<String, dynamic> map) {
  final type = _asAgentString(map['type'])?.toLowerCase();
  if (_remoteCodexBlockTypeLooksImage(type)) {
    return true;
  }
  final mimeType = _asAgentString(
    map['mimeType'] ??
        map['mime_type'] ??
        map['mediaType'] ??
        map['media_type'],
  )?.toLowerCase();
  if (mimeType?.startsWith('image/') == true) {
    return true;
  }
  for (final key in const <String>[
    'image',
    'imageUrl',
    'image_url',
    'dataUrl',
    'data_url',
  ]) {
    if (map.containsKey(key)) {
      return true;
    }
  }
  return false;
}

Map<String, dynamic>? _remoteCodexImageAttachmentFromBlock(
  Map<String, dynamic> map,
  int index,
) {
  final source =
      _remoteCodexImageStringFromValue(map['dataUrl']) ??
      _remoteCodexImageStringFromValue(map['data_url']) ??
      _remoteCodexImageStringFromValue(map['url']) ??
      _remoteCodexImageStringFromValue(map['imageUrl']) ??
      _remoteCodexImageStringFromValue(map['image_url']) ??
      _remoteCodexImageStringFromValue(map['image']) ??
      _remoteCodexImageStringFromValue(map['src']) ??
      _remoteCodexImageStringFromValue(map['source']);
  final path =
      _asAgentString(
        map['path'] ??
            map['filePath'] ??
            map['file_path'] ??
            map['filename'] ??
            map['fileName'],
      ) ??
      (source != null && !_remoteCodexImageSourceIsUrl(source) ? source : null);
  final url = source != null && _remoteCodexImageSourceIsUrl(source)
      ? source
      : null;
  final rawBase64 = _remoteCodexImageBase64FromBlock(map);
  final mimeType = _remoteCodexImageMimeType(
    explicit:
        map['mimeType'] ??
        map['mime_type'] ??
        map['mediaType'] ??
        map['media_type'],
    source: url,
    path: path,
  );
  final dataUrl = url?.startsWith('data:') == true
      ? url
      : (rawBase64 == null
            ? null
            : 'data:${mimeType ?? 'image/png'};base64,$rawBase64');
  final effectiveUrl = dataUrl ?? url;
  final effectivePath = dataUrl == null && url == null ? path : null;

  if ((effectiveUrl ?? '').isEmpty && (effectivePath ?? '').isEmpty) {
    return null;
  }

  final attachment = <String, dynamic>{
    'id': 'codex-image-$index',
    'name': _remoteCodexImageAttachmentName(
      map: map,
      source: effectiveUrl,
      path: effectivePath,
      mimeType: mimeType,
      index: index,
    ),
    'isImage': true,
    'sendToModel': true,
  };
  if (mimeType != null) {
    attachment['mimeType'] = mimeType;
  }
  if (dataUrl != null) {
    attachment['dataUrl'] = dataUrl;
  } else if (effectiveUrl != null) {
    attachment['url'] = effectiveUrl;
  }
  if (effectivePath != null) {
    attachment['path'] = effectivePath;
  }
  return attachment;
}

String? _remoteCodexImageStringFromValue(dynamic value) {
  if (value == null) return null;
  if (value is String) {
    final text = value.trim();
    return text.isEmpty ? null : text;
  }
  final map = _asAgentMap(value);
  if (map != null) {
    for (final key in const <String>[
      'url',
      'dataUrl',
      'data_url',
      'src',
      'source',
      'path',
    ]) {
      final nested = _remoteCodexImageStringFromValue(map[key]);
      if (nested != null) {
        return nested;
      }
    }
  }
  return null;
}

String? _remoteCodexImageBase64FromBlock(Map<String, dynamic> map) {
  final raw = _asAgentString(map['base64'] ?? map['b64_json']);
  if (raw == null || raw.startsWith('data:')) {
    return null;
  }
  return raw;
}

bool _remoteCodexImageSourceIsUrl(String value) {
  final normalized = value.trim().toLowerCase();
  return normalized.startsWith('data:') ||
      normalized.startsWith('http://') ||
      normalized.startsWith('https://');
}

String? _remoteCodexImageMimeType({
  required dynamic explicit,
  required String? source,
  required String? path,
}) {
  final explicitText = _asAgentString(explicit)?.toLowerCase();
  if (explicitText != null) {
    return explicitText.startsWith('image/')
        ? explicitText
        : 'image/$explicitText';
  }
  final dataMime = _remoteCodexMimeTypeFromDataUrl(source);
  if (dataMime != null) {
    return dataMime;
  }
  return _remoteCodexImageMimeTypeFromPath(path ?? source ?? '');
}

String? _remoteCodexMimeTypeFromDataUrl(String? value) {
  final source = value?.trim() ?? '';
  if (!source.toLowerCase().startsWith('data:')) {
    return null;
  }
  final comma = source.indexOf(',');
  final meta = comma == -1 ? source.substring(5) : source.substring(5, comma);
  final mime = meta.split(';').first.trim().toLowerCase();
  return mime.startsWith('image/') ? mime : null;
}

String? _remoteCodexImageMimeTypeFromPath(String value) {
  final path = value.split('?').first.split('#').first.toLowerCase();
  if (path.endsWith('.png')) return 'image/png';
  if (path.endsWith('.jpg') || path.endsWith('.jpeg')) return 'image/jpeg';
  if (path.endsWith('.gif')) return 'image/gif';
  if (path.endsWith('.webp')) return 'image/webp';
  if (path.endsWith('.bmp')) return 'image/bmp';
  if (path.endsWith('.heic')) return 'image/heic';
  if (path.endsWith('.heif')) return 'image/heif';
  return null;
}

String _remoteCodexImageAttachmentName({
  required Map<String, dynamic> map,
  required String? source,
  required String? path,
  required String? mimeType,
  required int index,
}) {
  final explicitName = _asAgentString(
    map['name'] ?? map['fileName'] ?? map['filename'],
  );
  if (explicitName != null) {
    return explicitName;
  }
  final pathName = _remoteCodexPathNameWithoutQuery(path);
  if (pathName != null) {
    return pathName;
  }
  final sourceName = _remoteCodexPathNameWithoutQuery(source);
  if (sourceName != null) {
    return sourceName;
  }
  final extension = switch (mimeType) {
    'image/jpeg' => 'jpg',
    'image/gif' => 'gif',
    'image/webp' => 'webp',
    'image/bmp' => 'bmp',
    'image/heic' => 'heic',
    'image/heif' => 'heif',
    _ => 'png',
  };
  return index == 0 ? 'image.$extension' : 'image-${index + 1}.$extension';
}

String? _remoteCodexPathNameWithoutQuery(String? value) {
  final raw = value?.trim() ?? '';
  if (raw.isEmpty || raw.toLowerCase().startsWith('data:')) {
    return null;
  }
  final withoutQuery = raw.split('?').first.split('#').first;
  return _remoteCodexLastPathSegment(withoutQuery);
}

String _remoteCodexExtractText(dynamic value) {
  if (value == null) return '';
  if (value is String) return value;
  if (value is num || value is bool) return value.toString();
  if (value is List) {
    return value
        .map(_remoteCodexExtractText)
        .where((text) => text.isNotEmpty)
        .join();
  }
  final map = _asAgentMap(value);
  if (map != null) {
    for (final key in const <String>[
      'text',
      'content',
      'message',
      'input',
      'value',
      'delta',
      'summary',
      'text_elements',
      'parts',
    ]) {
      final text = _remoteCodexExtractText(map[key]);
      if (text.isNotEmpty) {
        return text;
      }
    }
  }
  return value.toString();
}

int? _remoteCodexTimeValueMs(dynamic value) {
  if (value == null) return null;
  if (value is num) {
    final raw = value.toInt();
    return raw < 100000000000 ? raw * 1000 : raw;
  }
  final text = value.toString().trim();
  if (text.isEmpty) return null;
  final rawInt = int.tryParse(text);
  if (rawInt != null) {
    return rawInt < 100000000000 ? rawInt * 1000 : rawInt;
  }
  return DateTime.tryParse(text)?.millisecondsSinceEpoch;
}

String _truncateAgentText(String text, int maxLength) {
  final normalized = text.trim().replaceAll(RegExp(r'\s+'), ' ');
  if (normalized.length <= maxLength) {
    return normalized;
  }
  return '${normalized.substring(0, maxLength)}...';
}

String _safeAgentJson(dynamic value) {
  try {
    return const JsonEncoder.withIndent('  ').convert(value);
  } catch (_) {
    return value?.toString() ?? '';
  }
}

class _AgentHistoricalQuestion {
  const _AgentHistoricalQuestion({
    required this.id,
    required this.title,
    required this.detail,
  });

  final String id;
  final String title;
  final String detail;
}

_AgentHistoricalQuestion _remoteCodexHistoricalFirstQuestion(
  Map<String, dynamic> item,
) {
  final params = _asAgentMap(item['params']);
  final questions = item['questions'] ?? params?['questions'];
  if (questions is List && questions.isNotEmpty) {
    final first = _asAgentMap(questions.first);
    if (first != null) {
      final id =
          _asAgentString(first['id']) ??
          _asAgentString(first['questionId']) ??
          'answer';
      final title =
          _remoteCodexFirstText([
            first['label'],
            first['title'],
            first['question'],
          ]) ??
          'Agent needs input';
      final detail =
          _remoteCodexFirstText([first['description'], first['placeholder']]) ??
          title;
      return _AgentHistoricalQuestion(id: id, title: title, detail: detail);
    }
  }
  final id =
      _asAgentString(item['questionId']) ??
      _asAgentString(item['question_id']) ??
      _asAgentString(item['id']) ??
      'answer';
  final title =
      _remoteCodexFirstText([
        item['question'],
        item['title'],
        params?['question'],
        params?['title'],
      ]) ??
      'Agent needs input';
  final detail =
      _remoteCodexFirstText([
        item['description'],
        item['placeholder'],
        params?['description'],
        params?['placeholder'],
      ]) ??
      title;
  return _AgentHistoricalQuestion(id: id, title: title, detail: detail);
}

String _remoteCodexHistoricalApprovalTitle(Map<String, dynamic> item) {
  final command = _remoteCodexFirstText([
    item['command'],
    _asAgentMap(item['action'])?['command'],
    _asAgentMap(item['params'])?['command'],
  ]);
  if (command != null) {
    return _truncateAgentText(command, 48);
  }
  return 'Agent approval';
}

String _remoteCodexHistoricalApprovalDetail(Map<String, dynamic> item) {
  return _remoteCodexFirstText([
        item['reason'],
        item['description'],
        item['command'],
        _asAgentMap(item['params'])?['reason'],
        _asAgentMap(item['params'])?['description'],
        _asAgentMap(item['params'])?['command'],
      ]) ??
      _safeAgentJson(item);
}

String _remoteCodexHistoricalRequestStatus(
  Map<String, dynamic> item, {
  required String requestKind,
}) {
  final explicit = _remoteCodexNormalizeHistoricalRequestStatus(
    _remoteCodexFirstText([
      item['status'],
      item['state'],
      item['requestStatus'],
      item['request_status'],
      _asAgentMap(item['request'])?['status'],
      _asAgentMap(item['request'])?['state'],
    ]),
    requestKind: requestKind,
  );
  if (explicit != null && explicit != 'pending') {
    return explicit;
  }
  final response =
      item['response'] ??
      item['answer'] ??
      item['answers'] ??
      item['result'] ??
      item['decision'];
  if (_remoteCodexHasRequestResponse(response)) {
    if (requestKind == 'approval') {
      final decision = _remoteCodexNormalizeHistoricalRequestStatus(
        _remoteCodexFirstText([
          item['decision'],
          _asAgentMap(response)?['decision'],
          _asAgentMap(response)?['status'],
          _asAgentMap(response)?['state'],
        ]),
        requestKind: requestKind,
      );
      if (decision == 'accepted' || decision == 'declined') {
        return decision!;
      }
      return 'accepted';
    }
    return 'submitted';
  }
  return explicit ?? 'pending';
}

String? _remoteCodexNormalizeHistoricalRequestStatus(
  String? value, {
  required String requestKind,
}) {
  final normalized = value?.trim().toLowerCase() ?? '';
  if (normalized.isEmpty) {
    return null;
  }
  return switch (normalized) {
    'accept' || 'accepted' || 'approve' || 'approved' => 'accepted',
    'decline' || 'declined' || 'reject' || 'rejected' => 'declined',
    'submit' || 'submitted' || 'answered' => 'submitted',
    'complete' ||
    'completed' => requestKind == 'approval' ? 'accepted' : 'submitted',
    'fail' || 'failed' || 'error' => 'failed',
    'pending' || 'running' || 'requested' || 'open' => 'pending',
    _ => normalized,
  };
}

bool _remoteCodexHasRequestResponse(dynamic value) {
  if (value == null) {
    return false;
  }
  if (value is String) {
    return value.trim().isNotEmpty;
  }
  if (value is Iterable) {
    return value.isNotEmpty;
  }
  if (value is Map) {
    return value.isNotEmpty;
  }
  return true;
}

String? _remoteCodexFirstText(Iterable<dynamic> values) {
  for (final value in values) {
    final text = _remoteCodexExtractText(value).trim();
    if (text.isNotEmpty) {
      return text;
    }
  }
  return null;
}

const Set<String> _remoteCodexHistoricalToolItemTypes = <String>{
  'commandExecution',
  'local_shell_call',
  'commandExec',
  'processExecution',
  'fileChange',
  'tool',
  'mcpToolCall',
  'dynamicToolCall',
  'function_call',
  'custom_tool_call',
  'tool_search_call',
  'webSearch',
  'web_search_call',
  'imageView',
  'imageGeneration',
  'image_generation_call',
  'collabAgentToolCall',
  'collabToolCall',
  'plan',
};

const Set<String> _remoteCodexHistoricalRequestItemTypes = <String>{
  'requestUserInput',
  'requestApproval',
};

const Set<String> _remoteCodexHistoricalToolOutputItemTypes = <String>{
  'function_call_output',
  'custom_tool_call_output',
  'tool_search_output',
};

int _remoteCodexFindToolMessageIndexForCallId(
  List<ChatMessageModel> messages,
  String callId,
) {
  final normalizedCallId = callId.trim();
  if (normalizedCallId.isEmpty) {
    return -1;
  }
  for (var index = messages.length - 1; index >= 0; index -= 1) {
    final cardData = messages[index].cardData;
    if ((cardData?['type'] ?? '').toString() != 'agent_tool_summary') {
      continue;
    }
    if (_remoteCodexToolCardContainsCallId(cardData!, normalizedCallId)) {
      return index;
    }
  }
  return -1;
}

bool _remoteCodexToolCardContainsCallId(
  Map<String, dynamic> cardData,
  String callId,
) {
  for (final key in const <String>[
    'rawResultJson',
    'resultPreviewJson',
    'argsJson',
  ]) {
    final text = (cardData[key] ?? '').toString().trim();
    if (text.isEmpty) {
      continue;
    }
    try {
      if (_remoteCodexValueContainsCallId(jsonDecode(text), callId)) {
        return true;
      }
    } catch (_) {
      continue;
    }
  }
  return false;
}

bool _remoteCodexValueContainsCallId(dynamic value, String callId) {
  if (value == null) {
    return false;
  }
  if (value is String || value is num || value is bool) {
    return value.toString() == callId;
  }
  final map = _asAgentMap(value);
  if (map != null) {
    final direct =
        _asAgentString(map['callId']) ??
        _asAgentString(map['call_id']) ??
        _asAgentString(map['id']);
    if (direct == callId) {
      return true;
    }
    return map.values.any(
      (nested) => _remoteCodexValueContainsCallId(nested, callId),
    );
  }
  if (value is List) {
    return value.any(
      (nested) => _remoteCodexValueContainsCallId(nested, callId),
    );
  }
  return false;
}

String _remoteCodexRawOutputText(Map<String, dynamic> item) {
  final output = item['output'];
  final text = _remoteCodexExtractText(
    output ?? item['tools'] ?? item['result'] ?? item['content'],
  );
  if (text.trim().isNotEmpty) {
    return text;
  }
  if (output != null) {
    return _safeAgentJson(output);
  }
  return '';
}

String _remoteCodexStableItemKey(Map<String, dynamic> item) {
  final stablePayload = <String, dynamic>{
    'type': item['type'],
    'name': item['name'],
    'namespace': item['namespace'],
    'arguments': item['arguments'],
    'action': item['action'],
    'execution': item['execution'],
    'query': item['query'],
    'output': item['output'],
    'status': item['status'],
  };
  var hash = 0x811c9dc5;
  for (final codeUnit in _safeAgentJson(stablePayload).codeUnits) {
    hash ^= codeUnit;
    hash = (hash * 0x01000193) & 0xffffffff;
  }
  return 'raw-${hash.toRadixString(16).padLeft(8, '0')}';
}

String? _remoteCodexLastPathSegment(String path) {
  final normalized = path.trim().replaceAll(RegExp(r'/+$'), '');
  if (normalized.isEmpty) {
    return null;
  }
  final parts = normalized.split('/').where((part) => part.isNotEmpty).toList();
  if (parts.isEmpty) {
    return normalized == '/' ? '/' : null;
  }
  return parts.last;
}
