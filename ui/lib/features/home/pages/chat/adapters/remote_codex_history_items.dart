part of '../chat_page.dart';

List<Map<String, dynamic>> _remoteCodexHistoricalItemsFromTurn(
  Map<String, dynamic> turn,
) {
  final items = <Map<String, dynamic>>[];
  final seen = <String, int>{};

  void addItem(Map<String, dynamic> item) {
    final normalized = _remoteCodexNormalizeHistoricalItem(item);
    if (normalized == null) {
      return;
    }
    final key = _remoteCodexHistoricalItemDedupeKey(normalized);
    final existingIndex = seen[key];
    if (existingIndex != null) {
      items[existingIndex] = _remoteCodexMergeHistoricalItemSnapshot(
        items[existingIndex],
        normalized,
      );
      return;
    }
    seen[key] = items.length;
    items.add(normalized);
  }

  void addFromValue(dynamic value) {
    if (value is List) {
      for (final entry in value) {
        addFromValue(entry);
      }
      return;
    }
    final item = _remoteCodexHistoricalItemFromValue(value);
    if (item != null) {
      addItem(item);
    }
  }

  for (final key in const <String>[
    'items',
    'outputItems',
    'output_items',
    'responseItems',
    'response_items',
    'rawItems',
    'raw_items',
    'messages',
    'events',
    'inputItems',
    'input_items',
  ]) {
    addFromValue(turn[key]);
  }

  final worklog = _asAgentMap(turn['worklog']);
  addFromValue(worklog?['messages']);
  return items;
}

Map<String, dynamic> _remoteCodexMergeHistoricalItemSnapshot(
  Map<String, dynamic> existing,
  Map<String, dynamic> incoming,
) {
  final merged = Map<String, dynamic>.from(existing);
  for (final entry in incoming.entries) {
    final value = entry.value;
    if (value == null) {
      continue;
    }
    if (value is String && value.trim().isEmpty) {
      continue;
    }
    merged[entry.key] = value;
  }
  return merged;
}

Map<String, dynamic>? _remoteCodexHistoricalItemFromValue(dynamic value) {
  final map = _asAgentMap(value);
  if (map == null) {
    return null;
  }
  final direct = _remoteCodexNormalizeHistoricalItem(map);
  if (direct != null) {
    return direct;
  }
  for (final key in const <String>[
    'item',
    'rawItem',
    'raw_item',
    'responseItem',
    'response_item',
  ]) {
    final nested = _remoteCodexHistoricalItemFromValue(map[key]);
    if (nested != null) {
      return _remoteCodexMergeEnvelopeIds(map, nested);
    }
  }
  final params = _asAgentMap(map['params']);
  if (params != null) {
    final nested = _remoteCodexHistoricalItemFromValue(params);
    if (nested != null) {
      return _remoteCodexMergeEnvelopeIds(map, nested);
    }
  }
  final protocolItem = _remoteCodexHistoricalItemFromProtocolEvent(
    params ?? map,
  );
  if (protocolItem != null) {
    return _remoteCodexMergeEnvelopeIds(map, protocolItem);
  }
  final methodItem = _remoteCodexHistoricalItemFromEventMethod(
    _asAgentString(map['method'] ?? map['type']),
    params ?? map,
  );
  if (methodItem != null) {
    return _remoteCodexMergeEnvelopeIds(map, methodItem);
  }
  for (final key in _remoteCodexEnvelopeKeys) {
    if (key == 'params') {
      continue;
    }
    final nested = _remoteCodexHistoricalItemFromValue(map[key]);
    if (nested != null) {
      return _remoteCodexMergeEnvelopeIds(map, nested);
    }
  }
  return null;
}

Map<String, dynamic>? _remoteCodexHistoricalItemFromEventMethod(
  String? rawMethod,
  Map<String, dynamic> params,
) {
  final method = (rawMethod ?? '')
      .trim()
      .replaceAll('.', '/')
      .replaceAll('/command_execution/', '/commandExecution/')
      .replaceAll('/file_change/', '/fileChange/')
      .replaceAll('/mcp_tool_call/', '/mcpToolCall/');
  if (method.isEmpty) {
    return null;
  }
  final lowerMethod = method.toLowerCase();
  if (method.endsWith('requestUserInput') ||
      lowerMethod.endsWith('request_user_input')) {
    return _remoteCodexNormalizeHistoricalItem(<String, dynamic>{
      ...params,
      'id':
          _asAgentString(params['id']) ??
          _asAgentString(params['requestId']) ??
          _asAgentString(params['request_id']),
      'type': 'requestUserInput',
    });
  }
  if (method.endsWith('requestApproval') ||
      lowerMethod.endsWith('request_approval')) {
    return _remoteCodexNormalizeHistoricalItem(<String, dynamic>{
      ...params,
      'id':
          _asAgentString(params['id']) ??
          _asAgentString(params['requestId']) ??
          _asAgentString(params['request_id']),
      'type': 'requestApproval',
    });
  }
  if (method.contains('commandExecution') ||
      method == 'command/exec/outputDelta' ||
      method == 'command/exec/completed' ||
      method == 'process/outputDelta' ||
      method == 'process/exited') {
    return _remoteCodexNormalizeHistoricalItem(<String, dynamic>{
      ...params,
      'id':
          _asAgentString(
            params['itemId'] ??
                params['item_id'] ??
                params['processId'] ??
                params['process_id'] ??
                params['processHandle'] ??
                params['process_handle'],
          ) ??
          _asAgentString(params['id']),
      'type': method.contains('process')
          ? 'processExecution'
          : method.contains('command/exec')
          ? 'commandExec'
          : 'commandExecution',
      'aggregatedOutput':
          params['aggregatedOutput'] ??
          params['aggregated_output'] ??
          params['output'] ??
          params['delta'] ??
          params['text'],
      'status': params['status'] ?? 'completed',
    });
  }
  if (method.contains('fileChange') || method == 'turn/diff/updated') {
    return _remoteCodexNormalizeHistoricalItem(<String, dynamic>{
      ...params,
      'id':
          _asAgentString(params['itemId'] ?? params['item_id']) ??
          _asAgentString(params['id']),
      'type': 'fileChange',
      'status': params['status'] ?? 'completed',
    });
  }
  if (method.contains('mcpToolCall')) {
    return _remoteCodexNormalizeHistoricalItem(<String, dynamic>{
      ...params,
      'id':
          _asAgentString(params['itemId'] ?? params['item_id']) ??
          _asAgentString(params['id']),
      'type': 'mcpToolCall',
      'status': params['status'] ?? 'completed',
    });
  }
  return null;
}

Map<String, dynamic>? _remoteCodexHistoricalItemFromProtocolEvent(
  Map<String, dynamic> value,
) {
  final msg = _remoteCodexHistoricalProtocolMsg(value);
  if (msg == null) {
    return null;
  }
  final msgType = _remoteCodexNormalizeProtocolMsgType(
    _asAgentString(msg['type']),
  );
  if (msgType.isEmpty) {
    return null;
  }
  final eventId = _asAgentString(value['id']);
  final callId = _asAgentString(
    msg['callId'] ??
        msg['call_id'] ??
        msg['itemId'] ??
        msg['item_id'] ??
        msg['processId'] ??
        msg['process_id'] ??
        eventId,
  );
  Map<String, dynamic> withIds(Map<String, dynamic> item) {
    return <String, dynamic>{
      ..._remoteCodexTopLevelIds(value),
      ..._remoteCodexTopLevelIds(msg),
      if (callId != null) 'id': callId,
      ...item,
    };
  }

  switch (msgType) {
    case 'item_started':
    case 'item_completed':
      final item = _asAgentMap(msg['item']);
      return item == null ? null : _remoteCodexNormalizeHistoricalItem(item);
    case 'raw_response_item':
      final item = _asAgentMap(msg['item']);
      return item == null ? null : _remoteCodexNormalizeHistoricalItem(item);
    case 'agent_message':
      final text = _remoteCodexExtractText(msg['message'] ?? msg['text']);
      if (text.trim().isEmpty) {
        return null;
      }
      return withIds(<String, dynamic>{
        'type': 'agentMessage',
        'message': text,
      });
    case 'agent_reasoning':
    case 'agent_reasoning_raw_content':
    case 'reasoning_content_delta':
    case 'reasoning_raw_content_delta':
      final text = _remoteCodexExtractText(msg['delta'] ?? msg['text']);
      if (text.trim().isEmpty) {
        return null;
      }
      return withIds(<String, dynamic>{'type': 'reasoning', 'summary': text});
    case 'exec_command_begin':
    case 'exec_command_output_delta':
    case 'terminal_interaction':
    case 'exec_command_end':
      return _remoteCodexNormalizeHistoricalItem(
        withIds(_remoteCodexHistoricalCommandItem(msg, msgType: msgType)),
      );
    case 'mcp_tool_call_begin':
    case 'mcp_tool_call_end':
      return _remoteCodexNormalizeHistoricalItem(
        withIds(
          _remoteCodexHistoricalMcpToolItem(
            msg,
            completed: msgType.endsWith('_end'),
          ),
        ),
      );
    case 'web_search_begin':
    case 'web_search_end':
      return _remoteCodexNormalizeHistoricalItem(
        withIds(
          _remoteCodexHistoricalWebSearchItem(
            msg,
            completed: msgType.endsWith('_end'),
          ),
        ),
      );
    case 'view_image_tool_call':
      return _remoteCodexNormalizeHistoricalItem(
        withIds(<String, dynamic>{
          ...msg,
          'type': 'imageView',
          'status': 'completed',
        }),
      );
    case 'patch_apply_begin':
    case 'patch_apply_updated':
    case 'patch_apply_end':
      return _remoteCodexNormalizeHistoricalItem(
        withIds(
          _remoteCodexHistoricalPatchItem(
            msg,
            completed: msgType.endsWith('_end'),
          ),
        ),
      );
  }
  return null;
}

Map<String, dynamic>? _remoteCodexHistoricalProtocolMsg(
  Map<String, dynamic> root, {
  int depth = 0,
}) {
  if (depth > 6) {
    return null;
  }
  final direct = _asAgentMap(root['msg']);
  if (direct != null) {
    return direct;
  }
  for (final key in const <String>[
    'params',
    'message',
    'payload',
    'data',
    'event',
    'notification',
    'result',
  ]) {
    final nested = _asAgentMap(root[key]);
    if (nested == null) {
      continue;
    }
    final msg = _remoteCodexHistoricalProtocolMsg(nested, depth: depth + 1);
    if (msg != null) {
      return msg;
    }
  }
  return null;
}

String _remoteCodexNormalizeProtocolMsgType(String? rawType) {
  final value = rawType?.trim().toLowerCase() ?? '';
  if (value.isEmpty) {
    return '';
  }
  return value.replaceAll(RegExp(r'[^a-z0-9]+'), '_');
}

Map<String, dynamic> _remoteCodexTopLevelIds(Map<String, dynamic> value) {
  final ids = <String, dynamic>{};
  final meta = _asAgentMap(value['_meta']);
  if (meta != null) {
    for (final key in const <String>['threadId', 'thread_id']) {
      if (meta.containsKey(key)) {
        ids[key] = meta[key];
      }
    }
  }
  for (final key in const <String>[
    'threadId',
    'thread_id',
    'turnId',
    'turn_id',
    'itemId',
    'item_id',
  ]) {
    if (value.containsKey(key)) {
      ids[key] = value[key];
    }
  }
  return ids;
}

Map<String, dynamic> _remoteCodexHistoricalCommandItem(
  Map<String, dynamic> msg, {
  required String msgType,
}) {
  final command = _remoteCodexCommandTextFromValue(msg['command']);
  final exitCode = _asAgentInt(msg['exitCode'] ?? msg['exit_code']);
  final output = msgType == 'exec_command_output_delta'
      ? _remoteCodexHistoricalOutputDelta(msg)
      : _remoteCodexExtractText(
          msg['aggregatedOutput'] ??
              msg['aggregated_output'] ??
              msg['output'] ??
              msg['stdout'] ??
              msg['formattedOutput'] ??
              msg['formatted_output'],
        );
  final status =
      _asAgentString(msg['status']) ??
      (msgType == 'exec_command_begin'
          ? 'in_progress'
          : exitCode == null
          ? 'completed'
          : exitCode == 0
          ? 'completed'
          : 'failed');
  return <String, dynamic>{
    ...msg,
    'type': 'commandExecution',
    if (command != null) 'command': command,
    'cwd': msg['cwd'],
    'processId': msg['processId'] ?? msg['process_id'],
    'process_id': msg['process_id'] ?? msg['processId'],
    'aggregatedOutput': output,
    'aggregated_output': output,
    'stdout': msg['stdout'],
    'stderr': msg['stderr'],
    'exitCode': exitCode,
    'exit_code': exitCode,
    'status': status,
  };
}

Map<String, dynamic> _remoteCodexHistoricalMcpToolItem(
  Map<String, dynamic> msg, {
  required bool completed,
}) {
  final invocation =
      _asAgentMap(msg['invocation']) ?? const <String, dynamic>{};
  final resultFields = _remoteCodexHistoricalMcpResultFields(msg['result']);
  return <String, dynamic>{
    ...msg,
    'type': 'mcpToolCall',
    'server': invocation['server'] ?? msg['server'],
    'tool': invocation['tool'] ?? msg['tool'],
    'arguments': invocation['arguments'] ?? msg['arguments'],
    'status': completed
        ? (resultFields['status'] ?? msg['status'] ?? 'completed')
        : 'in_progress',
    ...resultFields,
  };
}

Map<String, dynamic> _remoteCodexHistoricalMcpResultFields(dynamic value) {
  if (value == null) {
    return const <String, dynamic>{};
  }
  final map = _asAgentMap(value);
  if (map != null) {
    if (map.containsKey('Ok') || map.containsKey('ok')) {
      return <String, dynamic>{
        'status': 'completed',
        'result': map['Ok'] ?? map['ok'],
      };
    }
    if (map.containsKey('Err') || map.containsKey('err')) {
      final error = map['Err'] ?? map['err'];
      return <String, dynamic>{
        'status': 'failed',
        'error': error is Map ? error : <String, dynamic>{'message': error},
      };
    }
  }
  return <String, dynamic>{'status': 'completed', 'result': value};
}

Map<String, dynamic> _remoteCodexHistoricalWebSearchItem(
  Map<String, dynamic> msg, {
  required bool completed,
}) {
  final action = _asAgentMap(msg['action']);
  return <String, dynamic>{
    ...msg,
    'type': 'webSearch',
    'query': msg['query'] ?? action?['query'],
    'status': completed ? 'completed' : 'in_progress',
  };
}

Map<String, dynamic> _remoteCodexHistoricalPatchItem(
  Map<String, dynamic> msg, {
  required bool completed,
}) {
  final success = msg['success'];
  return <String, dynamic>{
    ...msg,
    'type': 'fileChange',
    'changes': msg['changes'],
    'stdout': msg['stdout'],
    'stderr': msg['stderr'],
    'success': success,
    'status':
        _asAgentString(msg['status']) ??
        (completed
            ? success == false
                  ? 'failed'
                  : 'completed'
            : 'in_progress'),
  };
}

String? _remoteCodexCommandTextFromValue(dynamic value) {
  if (value == null) {
    return null;
  }
  if (value is String) {
    final text = value.trim();
    return text.isEmpty ? null : text;
  }
  if (value is List) {
    final parts = value
        .map(_remoteCodexExtractText)
        .map((part) => part.trim())
        .where((part) => part.isNotEmpty)
        .toList(growable: false);
    return parts.isEmpty ? null : parts.join(' ');
  }
  final text = _remoteCodexExtractText(value).trim();
  return text.isEmpty ? null : text;
}

String _remoteCodexHistoricalOutputDelta(Map<String, dynamic> msg) {
  final decoded =
      _decodeAgentBase64(msg['chunk']) ??
      _decodeAgentByteList(msg['chunk']) ??
      _decodeAgentBase64(msg['deltaBase64']) ??
      _decodeAgentBase64(msg['delta_base64']) ??
      _remoteCodexExtractText(msg['delta'] ?? msg['output'] ?? msg['text']);
  final stream = _asAgentString(msg['stream'])?.toLowerCase();
  if (decoded.isEmpty || stream == null || stream == 'stdout') {
    return decoded;
  }
  return '\n[$stream]\n$decoded${decoded.endsWith('\n') ? '' : '\n'}';
}

String? _decodeAgentBase64(dynamic value) {
  final encoded = _asAgentString(value);
  if (encoded == null) {
    return null;
  }
  try {
    return utf8.decode(base64Decode(encoded), allowMalformed: true);
  } catch (_) {
    return null;
  }
}

String? _decodeAgentByteList(dynamic value) {
  if (value is! List) {
    return null;
  }
  final bytes = <int>[];
  for (final item in value) {
    final byte = _asAgentInt(item);
    if (byte == null || byte < 0 || byte > 255) {
      return null;
    }
    bytes.add(byte);
  }
  try {
    return utf8.decode(bytes, allowMalformed: true);
  } catch (_) {
    return null;
  }
}

Map<String, dynamic> _remoteCodexMergeEnvelopeIds(
  Map<String, dynamic> envelope,
  Map<String, dynamic> item,
) {
  final merged = Map<String, dynamic>.from(item);
  for (final key in const <String>[
    'threadId',
    'thread_id',
    'turnId',
    'turn_id',
    'itemId',
    'item_id',
  ]) {
    if (!merged.containsKey(key) && envelope.containsKey(key)) {
      merged[key] = envelope[key];
    }
  }
  return merged;
}

Map<String, dynamic>? _remoteCodexNormalizeHistoricalItem(
  Map<String, dynamic> item,
) {
  final normalized = Map<String, dynamic>.from(item);
  var itemType = canonicalAgentItemType(_asAgentString(normalized['type']));
  final role = _asAgentString(
    normalized['role'] ?? _asAgentMap(normalized['author'])?['role'],
  )?.toLowerCase();
  if (itemType == 'message' || itemType.isEmpty) {
    if (role == 'user') {
      itemType = 'userMessage';
    } else if (role == 'assistant') {
      itemType = 'agentMessage';
    }
  }
  if ((itemType == 'output_diff' || itemType == 'pr') &&
      (normalized['diff'] != null || normalized['output_diff'] != null)) {
    itemType = 'fileChange';
    normalized['changes'] ??= normalized['diff'] ?? normalized['output_diff'];
  }
  if (itemType.isEmpty) {
    if (normalized['command'] != null || normalized['cmd'] != null) {
      itemType = 'commandExecution';
    } else if (normalized['name'] != null && normalized['arguments'] != null) {
      itemType = 'function_call';
    } else if ((normalized['callId'] != null ||
            normalized['call_id'] != null) &&
        normalized['output'] != null) {
      itemType = 'function_call_output';
    }
  }
  if (!_remoteCodexLooksLikeHistoricalItemType(itemType)) {
    return null;
  }
  normalized['type'] = itemType;
  return normalized;
}

bool _remoteCodexLooksLikeHistoricalItemType(String itemType) {
  final canonical = canonicalAgentItemType(itemType);
  return canonical == 'userMessage' ||
      canonical == 'agentMessage' ||
      canonical == 'reasoning' ||
      _remoteCodexHistoricalRequestItemTypes.contains(canonical) ||
      _remoteCodexHistoricalToolItemTypes.contains(canonical) ||
      _remoteCodexHistoricalToolOutputItemTypes.contains(canonical);
}

String _remoteCodexHistoricalItemDedupeKey(Map<String, dynamic> item) {
  final type = canonicalAgentItemType(_asAgentString(item['type']));
  final id =
      _asAgentString(
        item['id'] ??
            item['itemId'] ??
            item['item_id'] ??
            item['callId'] ??
            item['call_id'] ??
            item['processId'] ??
            item['process_id'] ??
            item['processHandle'] ??
            item['process_handle'],
      ) ??
      _remoteCodexStableItemKey(item);
  return '$type:$id';
}
