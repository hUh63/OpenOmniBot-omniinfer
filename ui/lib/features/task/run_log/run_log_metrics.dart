class RunLogMetrics {
  const RunLogMetrics({
    required this.startedAt,
    required this.durationMs,
    required this.model,
    required this.callCount,
    required this.tokenUsage,
  });

  final DateTime? startedAt;
  final int? durationMs;
  final String? model;
  final int? callCount;
  final RunLogTokenUsage tokenUsage;

  factory RunLogMetrics.fromPayload(Map<String, dynamic> payload) {
    final diagnostics = _map(payload['diagnostics']);
    final tokenUsage = RunLogTokenUsage.fromMap(
      _firstMap(diagnostics['token_usage'], payload['token_usage']),
    );
    final startedAtMs = _integer(payload['started_at_ms']);
    final finishedAtMs = _integer(payload['finished_at_ms']);
    final byCall = _mapList(
      diagnostics['token_usage_by_call'] ?? payload['token_usage_by_call'],
    );
    final byStep = _mapList(
      diagnostics['token_usage_by_step'] ?? payload['token_usage_by_step'],
    );
    final resolvedModels = _strings(
      tokenUsage.values['resolved_models'] ?? diagnostics['resolved_models'],
    );
    final model = _firstText([
      tokenUsage.values['resolved_model'],
      tokenUsage.values['model'],
      diagnostics['resolved_model'],
      diagnostics['model'],
      if (resolvedModels.isNotEmpty) resolvedModels.join(', '),
    ]);
    final durationMs =
        _integer(diagnostics['duration_ms'] ?? payload['duration_ms']) ??
        (startedAtMs != null && finishedAtMs != null
            ? (finishedAtMs - startedAtMs).coerceAtLeastZero
            : null);
    final callCount =
        _integer(tokenUsage.values['call_count']) ??
        (byCall.isNotEmpty ? byCall.length : null) ??
        _integer(tokenUsage.values['step_count']) ??
        (byStep.isNotEmpty ? byStep.length : null);
    return RunLogMetrics(
      startedAt: startedAtMs == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(startedAtMs).toLocal(),
      durationMs: durationMs,
      model: model,
      callCount: callCount,
      tokenUsage: tokenUsage,
    );
  }
}

extension on int {
  int get coerceAtLeastZero => this < 0 ? 0 : this;
}

class RunLogTokenUsage {
  const RunLogTokenUsage(this.values);

  final Map<String, dynamic> values;

  factory RunLogTokenUsage.fromMap(Map<String, dynamic> values) =>
      RunLogTokenUsage(values);

  int? get promptTokens =>
      _integer(values['prompt_tokens'] ?? values['promptTokens']);
  int? get completionTokens =>
      _integer(values['completion_tokens'] ?? values['completionTokens']);
  int? get totalTokens {
    final total = _integer(values['total_tokens'] ?? values['totalTokens']);
    if (total != null) return total;
    if (promptTokens == null && completionTokens == null) return null;
    return (promptTokens ?? 0) + (completionTokens ?? 0);
  }

  int? get cachedTokens =>
      _integer(values['cached_tokens'] ?? values['cachedTokens']);

  bool get hasUsage =>
      promptTokens != null ||
      completionTokens != null ||
      totalTokens != null ||
      cachedTokens != null;
}

RunLogTokenUsage? runLogStepTokenUsage(
  Map<String, dynamic> payload,
  Map<String, dynamic> step,
) {
  final metadata = _map(step['metadata']);
  final direct = _map(metadata['token_usage']);
  if (direct.isNotEmpty) return RunLogTokenUsage.fromMap(direct);

  final diagnostics = _map(payload['diagnostics']);
  final stepIndex = _integer(step['step_index']);
  final entries = _mapList(
    diagnostics['token_usage_by_step'] ?? payload['token_usage_by_step'],
  );
  for (final entry in entries) {
    if (_integer(entry['step_index']) != stepIndex) continue;
    final usage = _map(entry['token_usage']);
    if (usage.isNotEmpty) return RunLogTokenUsage.fromMap(usage);
  }
  return null;
}

String formatRunLogTimestamp(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')} '
    '${value.hour.toString().padLeft(2, '0')}:'
    '${value.minute.toString().padLeft(2, '0')}:'
    '${value.second.toString().padLeft(2, '0')}';

String formatRunLogDuration(int durationMs) {
  if (durationMs < 1000) return '$durationMs ms';
  if (durationMs < 60000) return '${_trimFixed(durationMs / 1000, 2)} s';
  final minutes = durationMs ~/ 60000;
  final seconds = (durationMs % 60000) / 1000;
  return '$minutes m ${_trimFixed(seconds, 2)} s';
}

String formatRunLogTokens(int tokens) {
  if (tokens >= 1000000) return '${(tokens / 1000000).toStringAsFixed(2)}M';
  if (tokens >= 1000) return '${(tokens / 1000).toStringAsFixed(2)}k';
  return tokens.toString();
}

String formatRunLogStepTokens(RunLogTokenUsage usage) {
  final total = usage.totalTokens;
  if (total == null) return '';
  final prompt = usage.promptTokens;
  final completion = usage.completionTokens;
  final split = prompt != null || completion != null
      ? ' · P${prompt == null ? '-' : formatRunLogTokens(prompt)}/'
            'C${completion == null ? '-' : formatRunLogTokens(completion)}'
      : '';
  return '${formatRunLogTokens(total)}$split';
}

String _trimFixed(double value, int fractionDigits) {
  final fixed = value.toStringAsFixed(fractionDigits);
  return fixed.replaceFirst(RegExp(r'\.?0+$'), '');
}

Map<String, dynamic> _firstMap(dynamic first, dynamic second) {
  final primary = _map(first);
  return primary.isNotEmpty ? primary : _map(second);
}

Map<String, dynamic> _map(dynamic value) => value is Map
    ? value.map((key, nested) => MapEntry(key.toString(), nested))
    : <String, dynamic>{};

List<Map<String, dynamic>> _mapList(dynamic value) => value is List
    ? value.whereType<Map>().map(_map).toList(growable: false)
    : const [];

int? _integer(dynamic value) {
  if (value is int) return value;
  if (value is num && value.isFinite) return value.toInt();
  return int.tryParse(value?.toString() ?? '');
}

List<String> _strings(dynamic value) => value is List
    ? value
          .map((entry) => entry?.toString().trim() ?? '')
          .where((entry) => entry.isNotEmpty)
          .toList(growable: false)
    : const [];

String? _firstText(Iterable<dynamic> values) {
  for (final value in values) {
    final text = value?.toString().trim() ?? '';
    if (text.isNotEmpty) return text;
  }
  return null;
}
