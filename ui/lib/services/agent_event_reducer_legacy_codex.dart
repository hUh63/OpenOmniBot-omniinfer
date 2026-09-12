// Legacy `codex/event` protocol handling for the remote PC Bridge.
//
// The bridge currently forwards the `codex app-server` protocol, whose ~28
// message types this file translates into the same timeline mutations the ACP
// path performs. Once the bridge forwards ACP instead, the Kotlin side maps
// session updates through `AcpSessionUpdateMapper` and nothing reaches this
// file any more.
//
// Retiring it then means: delete this file, plus in `agent_event_reducer.dart`
// the `part` directive and the `codex/event` dispatch in `reduce`. The other
// `codex/*` sites there are the bridge's transport diagnostics
// (`codex/stderr`, `codex/parseError`, emitted only by
// `RemoteCodexAppServerSession.kt`) and retire with the same change.
//
// Kept as a `part` so it can keep using the reducer's private helpers without
// widening their visibility, and so that removal is genuinely one file.
part of 'agent_event_reducer.dart';

AgentReduceResult? reduceLegacyCodexProtocolEvent(
  AgentEventReducer reducer, {
  required ChatConversationRuntimeState runtime,
  required Map<String, dynamic> event,
  required Map<String, dynamic> message,
  required Map<String, dynamic> params,
  required String fallbackParentTaskId,
  required String? fallbackThreadId,
  required String? fallbackTurnId,
}) {
  final msg =
      _remoteCodexProtocolMsg(params) ??
      _remoteCodexProtocolMsg(message) ??
      _remoteCodexProtocolMsg(event);
  if (msg == null) {
    return null;
  }
  final msgType = _normalizeRemoteCodexProtocolMsgType(_string(msg['type']));
  if (msgType.isEmpty) {
    return null;
  }

  final meta =
      _remoteCodexProtocolMeta(params) ??
      _remoteCodexProtocolMeta(message) ??
      _remoteCodexProtocolMeta(event);
  final protocolThreadId = _firstString([
    fallbackThreadId,
    params['threadId'],
    params['thread_id'],
    meta?['threadId'],
    meta?['thread_id'],
    msg['threadId'],
    msg['thread_id'],
    _asStringMap(msg['thread'])?['id'],
  ]);
  final protocolTurnId = _firstString([
    fallbackTurnId,
    params['turnId'],
    params['turn_id'],
    msg['turnId'],
    msg['turn_id'],
    _asStringMap(msg['turn'])?['id'],
  ]);
  final eventId = _firstString([params['id'], message['id'], event['id']]);
  final callId = _firstString([
    msg['callId'],
    msg['call_id'],
    msg['itemId'],
    msg['item_id'],
    msg['processId'],
    msg['process_id'],
    _asStringMap(msg['item'])?['id'],
    _asStringMap(msg['item'])?['callId'],
    _asStringMap(msg['item'])?['call_id'],
    eventId,
  ]);

  String taskIdFor({String? existingCardId}) {
    final existing = existingCardId == null
        ? null
        : reducer._toolCardData(runtime, existingCardId);
    return _firstString([
          protocolTurnId,
          existing?['taskId'],
          runtime.currentDispatchTaskId,
          runtime.lastAgentTaskId,
          callId,
          protocolThreadId,
          fallbackParentTaskId,
        ]) ??
        fallbackParentTaskId;
  }

  AgentReduceResult handled({bool handled = true}) {
    return AgentReduceResult(
      handled: handled,
      method: 'codex/event/$msgType',
      threadId: protocolThreadId,
      turnId: protocolTurnId,
      requestId: meta?['requestId'] ?? meta?['request_id'],
    );
  }

  Map<String, dynamic> lifecycleParams(Map<String, dynamic> item) {
    return <String, dynamic>{
      ..._topLevelAgentIds(params),
      ..._topLevelAgentIds(msg),
      if (protocolThreadId != null) 'threadId': protocolThreadId,
      if (protocolTurnId != null) 'turnId': protocolTurnId,
      if (callId != null) 'itemId': callId,
      'item': item,
    };
  }

  switch (msgType) {
    case 'task_started':
    case 'turn_started':
      reducer._touchActiveTurn(runtime, taskIdFor());
      return handled();
    case 'task_complete':
    case 'turn_complete':
    case 'turn_aborted':
      final lastMessage = _extractText(msg['last_agent_message']);
      final taskId = taskIdFor();
      if (lastMessage != null && lastMessage.trim().isNotEmpty) {
        reducer._appendAssistantText(
          runtime,
          parentTaskId: taskId,
          entryId: '$taskId-agent-message',
          delta: lastMessage,
          isFinal: true,
          replace: true,
        );
      }
      reducer._completeTurn(
        runtime,
        taskId,
        appendCancelIfEmpty: msgType == 'turn_aborted',
      );
      return handled();
    case 'agent_message':
      final text = _extractText(msg['message'] ?? msg['text']) ?? '';
      if (text.isNotEmpty) {
        final taskId = taskIdFor();
        reducer._appendAssistantText(
          runtime,
          parentTaskId: taskId,
          entryId: '${eventId ?? taskId}-agent-message',
          delta: text,
          isFinal: false,
          replace: true,
        );
      }
      return handled();
    case 'agent_message_content_delta':
      final delta = _extractText(msg['delta']) ?? '';
      if (delta.isNotEmpty) {
        final itemId =
            _firstString([msg['itemId'], msg['item_id'], callId]) ??
            taskIdFor();
        final taskId = taskIdFor();
        reducer._appendAssistantText(
          runtime,
          parentTaskId: taskId,
          entryId: '$itemId-agent-message',
          delta: delta,
          isFinal: false,
        );
      }
      return handled();
    case 'agent_reasoning':
    case 'agent_reasoning_raw_content':
    case 'reasoning_content_delta':
    case 'reasoning_raw_content_delta':
      final text =
          _extractText(msg['delta']) ?? _extractText(msg['text']) ?? '';
      if (text.isNotEmpty) {
        final itemId =
            _firstString([msg['itemId'], msg['item_id'], callId]) ??
            taskIdFor();
        final taskId = taskIdFor();
        reducer._appendThinking(
          runtime,
          parentTaskId: taskId,
          cardId: '$itemId-agent-thinking',
          delta: text,
        );
      }
      return handled();
    case 'plan_update':
    case 'plan_delta':
      final text =
          _extractText(msg['delta']) ??
          _extractText(msg['plan']) ??
          _safeJson(msg);
      final itemId =
          _firstString([msg['itemId'], msg['item_id'], callId]) ?? taskIdFor();
      final taskId = taskIdFor();
      reducer._upsertToolCard(
        runtime,
        cardId: '$itemId-agent-plan',
        taskId: taskId,
        toolType: 'plan',
        title: 'Agent plan',
        status: 'running',
        summary: text,
        progress: text,
        raw: <String, dynamic>{...msg, 'type': 'plan'},
        streamMeta: reducer._streamMeta(
          runtime,
          parentTaskId: taskId,
          entryId: '$itemId-agent-plan',
          kind: 'tool_progress',
        ),
      );
      return handled();
    case 'item_started':
      final item = _asStringMap(msg['item']);
      if (item == null) {
        return handled(handled: false);
      }
      return reducer.reduce(
        runtime: runtime,
        event: {'method': 'item/started', 'params': lifecycleParams(item)},
      );
    case 'item_completed':
      final item = _asStringMap(msg['item']);
      if (item == null) {
        return handled(handled: false);
      }
      return reducer.reduce(
        runtime: runtime,
        event: {'method': 'item/completed', 'params': lifecycleParams(item)},
      );
    case 'raw_response_item':
      final item = _asStringMap(msg['item']);
      if (item == null) {
        return handled(handled: false);
      }
      return reducer.reduce(
        runtime: runtime,
        event: {
          'method': 'rawResponseItem/completed',
          'params': lifecycleParams(item),
        },
      );
    case 'exec_command_begin':
      final item = _remoteCodexProtocolCommandItem(msg, status: 'running');
      final toolInfo = normalizeAgentToolCall(
        item,
        itemType: 'commandExecution',
        fallbackStatus: 'running',
      );
      final id = _firstString([item['id'], callId]) ?? taskIdFor();
      final suffix = agentToolCardSuffix(
        toolInfo.toolType,
        itemType: toolInfo.itemType,
      );
      final cardId = '$id-agent-$suffix';
      final taskId = taskIdFor(existingCardId: cardId);
      reducer._upsertToolCard(
        runtime,
        cardId: cardId,
        taskId: taskId,
        toolType: toolInfo.toolType,
        title: toolInfo.toolTitle,
        status: toolInfo.status,
        summary: toolInfo.summary,
        progress: toolInfo.progress,
        terminalOutput: toolInfo.terminalOutput,
        raw: item,
        streamMeta: reducer._streamMeta(
          runtime,
          parentTaskId: taskId,
          entryId: cardId,
          kind: 'tool_started',
        ),
      );
      return handled();
    case 'exec_command_output_delta':
    case 'terminal_interaction':
      final id = callId ?? taskIdFor();
      final existingCardId = callId == null
          ? null
          : reducer._findToolCardIdForCallId(runtime, callId);
      final existing = existingCardId == null
          ? null
          : reducer._toolCardData(runtime, existingCardId);
      final cardId = existingCardId ?? '$id-agent-command';
      final outputDelta = msgType == 'terminal_interaction'
          ? reducer._streamOutputBlock(msg['stdin'], stream: 'stdin')
          : _remoteCodexProtocolOutputDelta(msg);
      final taskId = taskIdFor(existingCardId: existingCardId);
      final toolType = (existing?['toolType'] ?? '').toString().trim();
      final title =
          (existing?['toolTitle'] ?? existing?['displayName'])?.toString() ??
          reducer._commandTitle(
            _remoteCodexProtocolCommandItem(msg, status: 'running'),
          );
      reducer._appendToolOutput(
        runtime,
        cardId: cardId,
        taskId: taskId,
        toolType: toolType.isEmpty ? 'terminal' : toolType,
        title: title,
        outputDelta: outputDelta,
        raw: _remoteCodexProtocolCommandItem(msg, status: 'running'),
        streamMeta: reducer._streamMeta(
          runtime,
          parentTaskId: taskId,
          entryId: cardId,
          kind: 'tool_progress',
        ),
      );
      return handled();
    case 'exec_command_end':
      final item = _remoteCodexProtocolCommandItem(msg, status: null);
      final id = _firstString([item['id'], callId]) ?? taskIdFor();
      final existingCardId = callId == null
          ? null
          : reducer._findToolCardIdForCallId(runtime, callId);
      final existing = existingCardId == null
          ? null
          : reducer._toolCardData(runtime, existingCardId);
      final toolInfo = normalizeAgentToolCall(
        item,
        itemType: 'commandExecution',
        fallbackToolType: (existing?['toolType'] ?? '').toString(),
        fallbackTitle: (existing?['toolTitle'] ?? existing?['displayName'])
            ?.toString(),
        fallbackStatus: 'success',
      );
      final suffix = agentToolCardSuffix(
        toolInfo.toolType,
        itemType: toolInfo.itemType,
      );
      final cardId = existingCardId ?? '$id-agent-$suffix';
      final taskId = taskIdFor(existingCardId: existingCardId);
      final existingOutput = (existing?['terminalOutput'] ?? '').toString();
      final finalOutput = _remoteCodexProtocolFinalCommandOutput(msg);
      final output = finalOutput.isNotEmpty ? finalOutput : existingOutput;
      final exitCode = _asInt(msg['exitCode'] ?? msg['exit_code']);
      final summary = exitCode == null
          ? 'Command completed'
          : 'Command exited with code $exitCode';
      reducer._upsertToolCard(
        runtime,
        cardId: cardId,
        taskId: taskId,
        toolType: toolInfo.toolType,
        title: toolInfo.toolTitle,
        status: toolInfo.status,
        summary: summary,
        progress: summary,
        terminalOutput: output,
        raw: item,
        streamMeta: reducer._streamMeta(
          runtime,
          parentTaskId: taskId,
          entryId: cardId,
          kind: 'tool_completed',
          isFinal: true,
        ),
        touchTurn: false,
      );
      runtime.agentReplayDeltaOffsets.remove(cardId);
      return handled();
    case 'mcp_tool_call_begin':
    case 'mcp_tool_call_end':
      final isEnd = msgType == 'mcp_tool_call_end';
      final item = _remoteCodexProtocolMcpToolItem(
        msg,
        status: isEnd ? null : 'running',
      );
      final id = _firstString([item['id'], callId]) ?? taskIdFor();
      final existingCardId = callId == null
          ? null
          : reducer._findToolCardIdForCallId(runtime, callId);
      final existing = existingCardId == null
          ? null
          : reducer._toolCardData(runtime, existingCardId);
      final toolInfo = normalizeAgentToolCall(
        item,
        itemType: 'mcpToolCall',
        fallbackToolType: (existing?['toolType'] ?? '').toString(),
        fallbackTitle: (existing?['toolTitle'] ?? existing?['displayName'])
            ?.toString(),
        fallbackStatus: isEnd ? 'success' : 'running',
      );
      final suffix = agentToolCardSuffix(
        toolInfo.toolType,
        itemType: toolInfo.itemType,
      );
      final cardId = existingCardId ?? '$id-agent-$suffix';
      final taskId = taskIdFor(existingCardId: existingCardId);
      reducer._upsertToolCard(
        runtime,
        cardId: cardId,
        taskId: taskId,
        toolType: toolInfo.toolType,
        title: toolInfo.toolTitle,
        status: toolInfo.status,
        summary: toolInfo.summary,
        progress: toolInfo.progress,
        terminalOutput: toolInfo.terminalOutput,
        raw: item,
        streamMeta: reducer._streamMeta(
          runtime,
          parentTaskId: taskId,
          entryId: cardId,
          kind: isEnd ? 'tool_completed' : 'tool_started',
          isFinal: isEnd,
        ),
        touchTurn: !isEnd,
      );
      if (isEnd) {
        runtime.agentReplayDeltaOffsets.remove(cardId);
      }
      return handled();
    case 'web_search_begin':
    case 'web_search_end':
      final isEnd = msgType == 'web_search_end';
      final item = _remoteCodexProtocolWebSearchItem(
        msg,
        status: isEnd ? 'completed' : 'running',
      );
      final toolInfo = normalizeAgentToolCall(
        item,
        itemType: 'webSearch',
        fallbackStatus: isEnd ? 'success' : 'running',
      );
      final id = _firstString([item['id'], callId]) ?? taskIdFor();
      final cardId = '$id-agent-search';
      final taskId = taskIdFor(existingCardId: cardId);
      reducer._upsertToolCard(
        runtime,
        cardId: cardId,
        taskId: taskId,
        toolType: toolInfo.toolType,
        title: toolInfo.toolTitle,
        status: toolInfo.status,
        summary: toolInfo.summary,
        progress: toolInfo.progress,
        raw: item,
        streamMeta: reducer._streamMeta(
          runtime,
          parentTaskId: taskId,
          entryId: cardId,
          kind: isEnd ? 'tool_completed' : 'tool_started',
          isFinal: isEnd,
        ),
        touchTurn: !isEnd,
      );
      return handled();
    case 'view_image_tool_call':
      final item = <String, dynamic>{
        ...msg,
        'id': callId,
        'type': 'imageView',
        'status': 'completed',
      };
      final toolInfo = normalizeAgentToolCall(
        item,
        itemType: 'imageView',
        fallbackStatus: 'success',
      );
      final id = callId ?? taskIdFor();
      final cardId = '$id-agent-image';
      final taskId = taskIdFor(existingCardId: cardId);
      reducer._upsertToolCard(
        runtime,
        cardId: cardId,
        taskId: taskId,
        toolType: toolInfo.toolType,
        title: toolInfo.toolTitle,
        status: toolInfo.status,
        summary: toolInfo.summary,
        progress: toolInfo.progress,
        raw: item,
        streamMeta: reducer._streamMeta(
          runtime,
          parentTaskId: taskId,
          entryId: cardId,
          kind: 'tool_completed',
          isFinal: true,
        ),
        touchTurn: false,
      );
      return handled();
    case 'patch_apply_begin':
    case 'patch_apply_updated':
    case 'patch_apply_end':
      final isEnd = msgType == 'patch_apply_end';
      final item = _remoteCodexProtocolPatchItem(
        msg,
        status: isEnd ? null : 'running',
      );
      final toolInfo = normalizeAgentToolCall(
        item,
        itemType: 'fileChange',
        fallbackStatus: isEnd ? 'success' : 'running',
      );
      final id = _firstString([item['id'], callId]) ?? taskIdFor();
      final cardId = '$id-agent-file';
      final taskId = taskIdFor(existingCardId: cardId);
      reducer._upsertToolCard(
        runtime,
        cardId: cardId,
        taskId: taskId,
        toolType: toolInfo.toolType,
        title: toolInfo.toolTitle,
        status: toolInfo.status,
        summary: toolInfo.summary,
        progress: toolInfo.progress,
        terminalOutput: toolInfo.terminalOutput,
        raw: item,
        streamMeta: reducer._streamMeta(
          runtime,
          parentTaskId: taskId,
          entryId: cardId,
          kind: isEnd ? 'tool_completed' : 'tool_progress',
          isFinal: isEnd,
        ),
        touchTurn: !isEnd,
      );
      return handled();
  }
  return null;
}

Map<String, dynamic>? _remoteCodexProtocolMsg(
  Map<String, dynamic> root, {
  int depth = 0,
}) {
  if (depth > 6) {
    return null;
  }
  final direct = _asStringMap(root['msg']);
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
    final nested = _asStringMap(root[key]);
    if (nested == null) {
      continue;
    }
    final msg = _remoteCodexProtocolMsg(nested, depth: depth + 1);
    if (msg != null) {
      return msg;
    }
  }
  return null;
}

Map<String, dynamic>? _remoteCodexProtocolMeta(
  Map<String, dynamic> root, {
  int depth = 0,
}) {
  if (depth > 6) {
    return null;
  }
  final direct = _asStringMap(root['_meta']);
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
    final nested = _asStringMap(root[key]);
    if (nested == null) {
      continue;
    }
    final meta = _remoteCodexProtocolMeta(nested, depth: depth + 1);
    if (meta != null) {
      return meta;
    }
  }
  return null;
}

String _normalizeRemoteCodexProtocolMsgType(String? rawType) {
  final value = rawType?.trim().toLowerCase() ?? '';
  if (value.isEmpty) {
    return '';
  }
  return value.replaceAll(RegExp(r'[^a-z0-9]+'), '_');
}

Map<String, dynamic> _remoteCodexProtocolCommandItem(
  Map<String, dynamic> msg, {
  required String? status,
}) {
  final command = _commandTextFromValue(msg['command']);
  final exitCode = _asInt(msg['exitCode'] ?? msg['exit_code']);
  final explicitStatus =
      status ??
      _string(msg['status']) ??
      (exitCode == null
          ? 'completed'
          : exitCode == 0
          ? 'completed'
          : 'failed');
  return <String, dynamic>{
    ...msg,
    'id': _firstString([msg['callId'], msg['call_id'], msg['id']]),
    'callId': _firstString([msg['callId'], msg['call_id']]),
    'call_id': _firstString([msg['call_id'], msg['callId']]),
    'type': 'commandExecution',
    if (command != null) 'command': command,
    'cwd': msg['cwd'],
    'processId': msg['processId'] ?? msg['process_id'],
    'process_id': msg['process_id'] ?? msg['processId'],
    'aggregatedOutput':
        msg['aggregatedOutput'] ?? msg['aggregated_output'] ?? msg['output'],
    'aggregated_output':
        msg['aggregated_output'] ?? msg['aggregatedOutput'] ?? msg['output'],
    'stdout': msg['stdout'],
    'stderr': msg['stderr'],
    'exitCode': exitCode,
    'exit_code': exitCode,
    'status': explicitStatus,
  };
}

String _remoteCodexProtocolOutputDelta(Map<String, dynamic> msg) {
  final decoded =
      _decodeBase64Output(msg['chunk']) ??
      _decodeByteListOutput(msg['chunk']) ??
      _decodeBase64Output(msg['deltaBase64']) ??
      _decodeBase64Output(msg['delta_base64']) ??
      _extractText(msg['delta']) ??
      _extractText(msg['output']) ??
      _extractText(msg['text']) ??
      '';
  final stream = _string(msg['stream'])?.toLowerCase();
  if (decoded.isEmpty || stream == null || stream == 'stdout') {
    return decoded;
  }
  return _remoteCodexProtocolStreamOutputBlock(decoded, stream: stream);
}

String _remoteCodexProtocolFinalCommandOutput(Map<String, dynamic> msg) {
  final aggregated =
      _extractText(msg['aggregatedOutput']) ??
      _extractText(msg['aggregated_output']) ??
      '';
  if (aggregated.isNotEmpty) {
    return _trimTerminalOutput(aggregated);
  }
  final stdout = _remoteCodexProtocolStreamOutputBlock(
    _extractText(msg['stdout']) ?? '',
    stream: 'stdout',
  );
  final stderr = _remoteCodexProtocolStreamOutputBlock(
    _extractText(msg['stderr']) ?? '',
    stream: 'stderr',
  );
  final combined = _trimTerminalOutput(stdout + stderr);
  if (combined.trim().isNotEmpty) {
    return combined;
  }
  return _trimTerminalOutput(
    _extractText(msg['formattedOutput'] ?? msg['formatted_output']) ?? '',
  );
}

String _remoteCodexProtocolStreamOutputBlock(
  String text, {
  required String stream,
}) {
  if (text.isEmpty) {
    return '';
  }
  final normalizedStream = stream.toLowerCase();
  if (normalizedStream == 'stdout') {
    return text;
  }
  final needsLeadingNewline = text.startsWith('\n') ? '' : '\n';
  final needsTrailingNewline = text.endsWith('\n') ? '' : '\n';
  return '$needsLeadingNewline[$normalizedStream]\n$text$needsTrailingNewline';
}

Map<String, dynamic> _remoteCodexProtocolMcpToolItem(
  Map<String, dynamic> msg, {
  required String? status,
}) {
  final invocation =
      _asStringMap(msg['invocation']) ?? const <String, dynamic>{};
  final resultFields = _remoteCodexProtocolMcpResultFields(msg['result']);
  return <String, dynamic>{
    ...msg,
    'id': _firstString([msg['callId'], msg['call_id'], msg['id']]),
    'callId': _firstString([msg['callId'], msg['call_id']]),
    'call_id': _firstString([msg['call_id'], msg['callId']]),
    'type': 'mcpToolCall',
    'server': invocation['server'] ?? msg['server'],
    'tool': invocation['tool'] ?? msg['tool'],
    'arguments': invocation['arguments'] ?? msg['arguments'],
    'mcpAppResourceUri':
        msg['mcpAppResourceUri'] ?? msg['mcp_app_resource_uri'],
    'pluginId': msg['pluginId'] ?? msg['plugin_id'],
    'status': status ?? resultFields['status'] ?? msg['status'] ?? 'completed',
    ...resultFields,
  };
}

Map<String, dynamic> _remoteCodexProtocolMcpResultFields(dynamic value) {
  if (value == null) {
    return const <String, dynamic>{};
  }
  final map = _asStringMap(value);
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

Map<String, dynamic> _remoteCodexProtocolWebSearchItem(
  Map<String, dynamic> msg, {
  required String status,
}) {
  final action = _asStringMap(msg['action']);
  return <String, dynamic>{
    ...msg,
    'id': _firstString([msg['callId'], msg['call_id'], msg['id']]),
    'callId': _firstString([msg['callId'], msg['call_id']]),
    'call_id': _firstString([msg['call_id'], msg['callId']]),
    'type': 'webSearch',
    'query': msg['query'] ?? action?['query'],
    'action': msg['action'],
    'status': status,
  };
}

Map<String, dynamic> _remoteCodexProtocolPatchItem(
  Map<String, dynamic> msg, {
  required String? status,
}) {
  final success = msg['success'];
  final normalizedStatus =
      status ??
      _string(msg['status']) ??
      (success == false ? 'failed' : 'completed');
  return <String, dynamic>{
    ...msg,
    'id': _firstString([msg['callId'], msg['call_id'], msg['id']]),
    'callId': _firstString([msg['callId'], msg['call_id']]),
    'call_id': _firstString([msg['call_id'], msg['callId']]),
    'type': 'fileChange',
    'changes': msg['changes'],
    'stdout': msg['stdout'],
    'stderr': msg['stderr'],
    'success': success,
    'status': normalizedStatus,
  };
}
