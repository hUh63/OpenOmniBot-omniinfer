import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:ui/models/conversation_model.dart';
import 'package:ui/services/agent_runtime_service.dart';
import 'package:ui/services/conversation_history_service.dart';
import 'package:ui/services/storage_service.dart';
import 'package:ui/theme/theme_context.dart';

class AgentRequestCard extends StatefulWidget {
  const AgentRequestCard({super.key, required this.cardData});

  final Map<String, dynamic> cardData;

  @override
  State<AgentRequestCard> createState() => _AgentRequestCardState();
}

class _AgentRequestCardState extends State<AgentRequestCard>
    with WidgetsBindingObserver {
  final TextEditingController _answerController = TextEditingController();
  final FocusNode _answerFocusNode = FocusNode(
    debugLabel: 'agent_request_answer',
  );
  final GlobalKey _answerInputKey = GlobalKey(
    debugLabel: 'agent_request_answer_input',
  );
  Timer? _ensureAnswerInputTimer;
  Timer? _lateEnsureAnswerInputTimer;
  bool _isSubmitting = false;
  String? _localStatus;
  List<String> _localAnswers = const <String>[];
  String? _selectedOptionValue;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _answerFocusNode.addListener(_handleAnswerFocusChanged);
    _syncDefaultSelection();
    _hydratePersistedResponse();
  }

  @override
  void didUpdateWidget(covariant AgentRequestCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_requestRenderSignature(oldWidget.cardData) !=
        _requestRenderSignature(widget.cardData)) {
      _localStatus = null;
      _localAnswers = const <String>[];
      _selectedOptionValue = null;
      _answerController.clear();
      _syncDefaultSelection();
      _hydratePersistedResponse();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _ensureAnswerInputTimer?.cancel();
    _lateEnsureAnswerInputTimer?.cancel();
    _answerFocusNode
      ..removeListener(_handleAnswerFocusChanged)
      ..dispose();
    _answerController.dispose();
    super.dispose();
  }

  @override
  void didChangeMetrics() {
    _scheduleEnsureAnswerInputVisible();
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final kind = (widget.cardData['requestKind'] ?? '').toString();
    final agentName = (widget.cardData['agentName'] ?? 'Agent')
        .toString()
        .trim();
    final title =
        (widget.cardData['title'] ??
                '${agentName.isEmpty ? 'Agent' : agentName} request')
            .toString();
    final detail = _requestVisibleDetail(
      title,
      (widget.cardData['detail'] ?? '').toString(),
    );
    final cardStatus = _cardStatus(widget.cardData);
    final status = cardStatus == 'pending'
        ? (_isTerminalRequestStatus(_localStatus) ? _localStatus! : 'pending')
        : (_localStatus ?? cardStatus);
    final isPending = status == 'pending' && !_isSubmitting;
    final options = _resolveRequestOptions(widget.cardData);
    final hasOptions = options.isNotEmpty;
    final answers = _localAnswers.isNotEmpty
        ? _localAnswers
        : _stringList(widget.cardData['submittedAnswers']);
    final canSubmit =
        isPending &&
        (!hasOptions ||
            _selectedOptionValue != null ||
            _answerController.text.trim().isNotEmpty);

    return Container(
      key: const ValueKey('agent-request-card-surface'),
      width: double.infinity,
      margin: const EdgeInsets.only(top: 8, bottom: 4),
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 14),
      decoration: BoxDecoration(
        color: context.isDarkTheme
            ? palette.surfaceSecondary
            : const Color(0xFFFDFDFE),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: context.isDarkTheme
              ? palette.borderSubtle
              : const Color(0xFFE0E3E7),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w800,
              color: palette.textPrimary,
              height: 1.2,
            ),
          ),
          if (detail.trim().isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              detail,
              maxLines: 5,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 12,
                color: palette.textSecondary,
                height: 1.35,
              ),
            ),
          ],
          const SizedBox(height: 14),
          if (kind == 'user_input' && status == 'pending') ...[
            if (hasOptions) ...[
              for (var index = 0; index < options.length; index++) ...[
                _RequestOptionTile(
                  index: index + 1,
                  option: options[index],
                  selected: options[index].value == _selectedOptionValue,
                  enabled: isPending,
                  onTap: () {
                    setState(() {
                      _selectedOptionValue = options[index].value;
                      _answerController.clear();
                    });
                  },
                ),
                const SizedBox(height: 4),
              ],
              _CustomAnswerInput(
                inputKey: _answerInputKey,
                controller: _answerController,
                focusNode: _answerFocusNode,
                agentName: agentName.isEmpty ? 'Agent' : agentName,
                enabled: isPending,
                onTap: () {
                  setState(() {
                    _selectedOptionValue = null;
                  });
                  _scheduleEnsureAnswerInputVisible();
                },
                onChanged: (value) {
                  setState(() {
                    if (value.trim().isNotEmpty) {
                      _selectedOptionValue = null;
                    }
                  });
                },
              ),
              const SizedBox(height: 12),
            ] else ...[
              TextField(
                controller: _answerController,
                minLines: 1,
                maxLines: 3,
                style: TextStyle(fontSize: 12, color: palette.textPrimary),
                decoration: InputDecoration(
                  isDense: true,
                  hintText: 'Answer',
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 8,
                  ),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(6),
                  ),
                ),
              ),
              const SizedBox(height: 8),
            ],
          ],
          _RequestFooter(
            kind: kind,
            status: status,
            answers: answers,
            isPending: isPending,
            isSubmitting: _isSubmitting,
            canSubmit: canSubmit,
            onAccept: () => _respondApproval(true),
            onDecline: () => _respondApproval(false),
            onIgnore: _ignoreUserInput,
            onSubmit: _respondUserInput,
          ),
        ],
      ),
    );
  }

  Future<void> _respondApproval(bool accepted) async {
    final requestId = widget.cardData['requestId'];
    if (requestId == null) return;
    await _submit(() {
      return AgentRuntimeService.respondToApproval(
        requestId: requestId,
        accepted: accepted,
      );
    }, accepted ? 'accepted' : 'declined');
  }

  Future<void> _ignoreUserInput() async {
    final requestId = widget.cardData['requestId'];
    if (requestId == null) return;
    await _submit(() {
      return AgentRuntimeService.ignoreUserInput(requestId: requestId);
    }, 'ignored');
  }

  Future<void> _respondUserInput() async {
    final requestId = widget.cardData['requestId'];
    final questionId = (widget.cardData['questionId'] ?? 'answer').toString();
    if (requestId == null) return;
    final customAnswer = _answerController.text.trim();
    final answer = customAnswer.isNotEmpty
        ? customAnswer
        : (_selectedOptionValue ?? '').trim();
    if (answer.isEmpty) return;
    await _submit(
      () {
        return AgentRuntimeService.respondToUserInput(
          requestId: requestId,
          questionId: questionId,
          answers: <String>[answer],
        );
      },
      'submitted',
      answers: <String>[answer],
    );
  }

  Future<void> _submit(
    Future<Map<String, dynamic>> Function() action,
    String successStatus, {
    List<String> answers = const <String>[],
  }) async {
    if (_isSubmitting) return;
    setState(() {
      _isSubmitting = true;
    });
    try {
      await action();
      await _persistResponseStatus(successStatus, answers);
      if (!mounted) return;
      setState(() {
        _localStatus = successStatus;
        _localAnswers = answers;
        _isSubmitting = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _localStatus = 'failed';
        _isSubmitting = false;
      });
    }
  }

  void _handleAnswerFocusChanged() {
    if (!mounted) {
      return;
    }
    setState(() {});
    _scheduleEnsureAnswerInputVisible();
  }

  void _scheduleEnsureAnswerInputVisible() {
    if (!_answerFocusNode.hasFocus) {
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _ensureAnswerInputVisible();
    });
    _ensureAnswerInputTimer?.cancel();
    _lateEnsureAnswerInputTimer?.cancel();
    _ensureAnswerInputTimer = Timer(const Duration(milliseconds: 260), () {
      _ensureAnswerInputVisible();
    });
    _lateEnsureAnswerInputTimer = Timer(const Duration(milliseconds: 560), () {
      _ensureAnswerInputVisible();
    });
  }

  void _ensureAnswerInputVisible() {
    if (!mounted || !_answerFocusNode.hasFocus) {
      return;
    }
    final inputContext = _answerInputKey.currentContext;
    if (inputContext == null) {
      return;
    }
    Scrollable.ensureVisible(
      inputContext,
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOutCubic,
      alignment: 0.72,
      alignmentPolicy: ScrollPositionAlignmentPolicy.keepVisibleAtEnd,
    );
  }

  void _hydratePersistedResponse() {
    try {
      final raw =
          StorageService.getString(_requestStorageKey(widget.cardData)) ??
          StorageService.getString(_legacyRequestStorageKey(widget.cardData));
      if (raw == null || raw.trim().isEmpty) {
        return;
      }
      final decoded = jsonDecode(raw);
      if (decoded is! Map) {
        return;
      }
      final status = decoded['status']?.toString().trim().toLowerCase();
      if (status == null || status.isEmpty) {
        return;
      }
      if (!_isTerminalRequestStatus(status)) {
        return;
      }
      final currentIdentity = _requestStorageIdentity(widget.cardData);
      final cachedIdentity = decoded['identity']?.toString().trim();
      if (cachedIdentity != null &&
          cachedIdentity.isNotEmpty &&
          cachedIdentity != currentIdentity) {
        return;
      }
      if (_cardStatus(widget.cardData) == 'pending' &&
          cachedIdentity != currentIdentity) {
        return;
      }
      _localStatus = status;
      _localAnswers = _stringList(decoded['answers']);
    } catch (_) {
      return;
    }
  }

  void _syncDefaultSelection() {
    if (_cardStatus(widget.cardData) != 'pending') {
      return;
    }
    final options = _resolveRequestOptions(widget.cardData);
    if (options.isEmpty || _selectedOptionValue != null) {
      return;
    }
    if (_answerController.text.trim().isNotEmpty) {
      return;
    }
    _selectedOptionValue = options.first.value;
  }

  Future<void> _persistResponseStatus(
    String status,
    List<String> answers,
  ) async {
    final nextCardData = Map<String, dynamic>.from(widget.cardData)
      ..['status'] = status
      ..['submittedAnswers'] = answers;
    widget.cardData['status'] = status;
    widget.cardData['submittedAnswers'] = answers;

    final conversationId = _asInt(widget.cardData['conversationId']);
    final cardId = (widget.cardData['cardId'] ?? widget.cardData['id'] ?? '')
        .toString()
        .trim();
    if (conversationId != null && cardId.isNotEmpty) {
      await ConversationHistoryService.upsertConversationUiCard(
        conversationId,
        entryId: cardId,
        cardData: nextCardData,
        createdAtMillis: _asInt(widget.cardData['startTime']),
        mode: ConversationMode.agent,
      );
    }
    try {
      final identity = _requestStorageIdentity(widget.cardData);
      await StorageService.setString(
        _requestStorageKey(widget.cardData),
        jsonEncode(<String, dynamic>{
          'identity': identity,
          'status': status,
          'answers': answers,
        }),
      );
    } catch (_) {
      return;
    }
  }
}

class _RequestOptionTile extends StatelessWidget {
  const _RequestOptionTile({
    required this.index,
    required this.option,
    required this.selected,
    required this.enabled,
    required this.onTap,
  });

  final int index;
  final _RequestOption option;
  final bool selected;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final foreground = enabled ? palette.textPrimary : palette.textTertiary;
    final secondary = enabled ? palette.textSecondary : palette.textTertiary;
    final selectedTextColor = context.isDarkTheme
        ? palette.surfacePrimary
        : Colors.white;
    final selectedCircleColor = context.isDarkTheme
        ? palette.textPrimary
        : const Color(0xFF20242B);
    final unselectedCircleBorder = context.isDarkTheme
        ? palette.borderSubtle
        : const Color(0xFFDADDE2);
    return Material(
      key: ValueKey('agent-request-option-row-$index'),
      color: selected
          ? (context.isDarkTheme
                ? palette.surfaceElevated.withValues(alpha: 0.82)
                : const Color(0xFFF1F1F2))
          : Colors.transparent,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        onTap: enabled ? onTap : null,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 9),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                width: 24,
                height: 24,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: selected ? selectedCircleColor : Colors.transparent,
                  border: selected
                      ? null
                      : Border.all(color: unselectedCircleBorder),
                ),
                child: Text(
                  '$index',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    height: 1,
                    color: selected ? selectedTextColor : secondary,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Wrap(
                  spacing: 10,
                  runSpacing: 2,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(
                      option.label,
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: foreground,
                        height: 1.25,
                      ),
                    ),
                    if (option.description.isNotEmpty)
                      Text(
                        option.description,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: secondary,
                          height: 1.25,
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CustomAnswerInput extends StatelessWidget {
  const _CustomAnswerInput({
    required this.inputKey,
    required this.controller,
    required this.focusNode,
    required this.agentName,
    required this.enabled,
    required this.onTap,
    required this.onChanged,
  });

  final Key inputKey;
  final TextEditingController controller;
  final FocusNode focusNode;
  final String agentName;
  final bool enabled;
  final VoidCallback onTap;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final isEnglish =
        Localizations.maybeLocaleOf(context)?.languageCode == 'en';
    final hint = isEnglish
        ? 'No, tell $agentName how to adjust'
        : '否，请告知 $agentName 如何调整';
    final view = View.of(context);
    final viewKeyboardInset = view.viewInsets.bottom / view.devicePixelRatio;
    final mediaQueryKeyboardInset =
        MediaQuery.maybeOf(context)?.viewInsets.bottom ?? 0.0;
    final keyboardInset = viewKeyboardInset > mediaQueryKeyboardInset
        ? viewKeyboardInset
        : mediaQueryKeyboardInset;

    return KeyedSubtree(
      key: inputKey,
      child: SizedBox(
        key: const ValueKey('agent-request-custom-answer-input'),
        width: double.infinity,
        child: TextField(
          controller: controller,
          focusNode: focusNode,
          enabled: enabled,
          minLines: 1,
          maxLines: 1,
          keyboardType: TextInputType.text,
          textInputAction: TextInputAction.done,
          scrollPhysics: const ClampingScrollPhysics(),
          scrollPadding: EdgeInsets.only(top: 24, bottom: keyboardInset + 96),
          onTap: onTap,
          onChanged: onChanged,
          textCapitalization: TextCapitalization.sentences,
          style: context.omniInputTextStyle,
          decoration: InputDecoration(
            labelText: hint,
            hintText: isEnglish ? 'Describe the adjustment' : '请输入调整说明',
          ),
        ),
      ),
    );
  }
}

class _RequestFooter extends StatelessWidget {
  const _RequestFooter({
    required this.kind,
    required this.status,
    required this.answers,
    required this.isPending,
    required this.isSubmitting,
    required this.canSubmit,
    required this.onAccept,
    required this.onDecline,
    required this.onIgnore,
    required this.onSubmit,
  });

  final String kind;
  final String status;
  final List<String> answers;
  final bool isPending;
  final bool isSubmitting;
  final bool canSubmit;
  final VoidCallback onAccept;
  final VoidCallback onDecline;
  final VoidCallback onIgnore;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final isEnglish =
        Localizations.maybeLocaleOf(context)?.languageCode == 'en';
    if (isSubmitting) {
      return Align(
        alignment: Alignment.centerRight,
        child: SizedBox(
          width: 18,
          height: 18,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            color: palette.accentPrimary,
          ),
        ),
      );
    }
    if (status != 'pending') {
      return Text(
        answers.isEmpty ? status : '$status: ${answers.join(', ')}',
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: palette.textSecondary,
        ),
      );
    }
    if (kind == 'approval') {
      return Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          TextButton(
            onPressed: isPending ? onDecline : null,
            child: Text(isEnglish ? 'Decline' : '拒绝'),
          ),
          const SizedBox(width: 6),
          FilledButton(
            onPressed: isPending ? onAccept : null,
            child: Text(isEnglish ? 'Accept' : '接受'),
          ),
        ],
      );
    }
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        TextButton(
          onPressed: isPending ? onIgnore : null,
          style: TextButton.styleFrom(
            minimumSize: const Size(0, 36),
            padding: const EdgeInsets.symmetric(horizontal: 10),
            foregroundColor: palette.textSecondary,
            disabledForegroundColor: palette.textTertiary,
          ),
          child: Text(
            isEnglish ? 'Ignore' : '忽略',
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
          ),
        ),
        const SizedBox(width: 8),
        FilledButton(
          onPressed: canSubmit ? onSubmit : null,
          style: FilledButton.styleFrom(
            minimumSize: const Size(0, 36),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            backgroundColor: const Color(0xFF2D99FF),
            disabledBackgroundColor: context.isDarkTheme
                ? palette.surfaceElevated
                : const Color(0xFFE2E5E9),
            foregroundColor: Colors.white,
            disabledForegroundColor: palette.textTertiary,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(18),
            ),
          ),
          child: Text(
            isEnglish ? 'Submit ↵' : '提交 ↵',
            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800),
          ),
        ),
      ],
    );
  }
}

class _RequestOption {
  const _RequestOption({
    required this.label,
    required this.value,
    this.description = '',
  });

  final String label;
  final String value;
  final String description;
}

List<_RequestOption> _resolveRequestOptions(Map<String, dynamic> cardData) {
  final raw = _decodeRawParams(cardData['rawParamsJson']);
  final questionId = (cardData['questionId'] ?? '').toString().trim();
  final question = _resolveQuestion(raw, questionId);
  final optionSource =
      question?['options'] ??
      question?['choices'] ??
      question?['items'] ??
      raw['options'] ??
      raw['choices'];
  if (optionSource is! List) {
    return const <_RequestOption>[];
  }
  final seen = <String>{};
  final options = <_RequestOption>[];
  for (final item in optionSource) {
    final option = _requestOptionFromValue(item);
    if (option == null || !seen.add(option.value)) {
      continue;
    }
    options.add(option);
  }
  return options;
}

Map<String, dynamic> _decodeRawParams(dynamic rawParamsJson) {
  final raw = rawParamsJson?.toString().trim() ?? '';
  if (raw.isEmpty) {
    return const <String, dynamic>{};
  }
  try {
    final decoded = jsonDecode(raw);
    if (decoded is Map) {
      return decoded.map((key, value) => MapEntry(key.toString(), value));
    }
  } catch (_) {
    return const <String, dynamic>{};
  }
  return const <String, dynamic>{};
}

Map<String, dynamic>? _resolveQuestion(
  Map<String, dynamic> raw,
  String questionId,
) {
  final questions = raw['questions'];
  if (questions is! List || questions.isEmpty) {
    return null;
  }
  for (final item in questions) {
    final map = _asStringMap(item);
    if (map == null) {
      continue;
    }
    final id = (map['id'] ?? map['questionId'] ?? '').toString();
    if (questionId.isNotEmpty && id == questionId) {
      return map;
    }
  }
  return _asStringMap(questions.first);
}

_RequestOption? _requestOptionFromValue(dynamic value) {
  if (value is String || value is num || value is bool) {
    final label = value.toString().trim();
    return label.isEmpty ? null : _RequestOption(label: label, value: label);
  }
  final map = _asStringMap(value);
  if (map == null) {
    return null;
  }
  final label =
      _firstText([
        map['label'],
        map['title'],
        map['name'],
        map['value'],
        map['id'],
      ]) ??
      '';
  if (label.isEmpty) {
    return null;
  }
  final optionValue =
      _firstText([map['value'], map['id'], map['label']]) ?? label;
  final description =
      _firstText([map['description'], map['detail'], map['subtitle']]) ?? '';
  return _RequestOption(
    label: label,
    value: optionValue,
    description: description,
  );
}

String _requestStorageKey(Map<String, dynamic> cardData) {
  return 'agent_request_response.${_requestStorageIdentity(cardData)}';
}

String _legacyRequestStorageKey(Map<String, dynamic> cardData) {
  return 'codex_request_response.${_requestStorageIdentity(cardData)}';
}

String _requestStorageIdentity(Map<String, dynamic> cardData) {
  final parts = <String>[
    (cardData['requestId'] ?? '').toString().trim(),
    (cardData['cardId'] ?? cardData['id'] ?? '').toString().trim(),
    (cardData['questionId'] ?? '').toString().trim(),
    (cardData['startTime'] ?? '').toString().trim(),
  ].where((part) => part.isNotEmpty).toList(growable: false);
  if (parts.isEmpty) {
    return 'unknown';
  }
  return parts.join('.');
}

String _requestRenderSignature(Map<String, dynamic> cardData) {
  return [
    _requestStorageIdentity(cardData),
    _cardStatus(cardData),
    (cardData['rawParamsJson'] ?? '').toString(),
  ].join('|');
}

String _cardStatus(Map<String, dynamic> cardData) {
  final normalized = (cardData['status'] ?? 'pending')
      .toString()
      .trim()
      .toLowerCase();
  return normalized.isEmpty ? 'pending' : normalized;
}

bool _isTerminalRequestStatus(String? status) {
  return status == 'submitted' ||
      status == 'ignored' ||
      status == 'accepted' ||
      status == 'declined';
}

String _requestVisibleDetail(String title, String detail) {
  final normalizedTitle = _normalizeComparableText(title);
  final normalizedDetail = _normalizeComparableText(detail);
  if (normalizedDetail.isEmpty || normalizedDetail == normalizedTitle) {
    return '';
  }
  return detail;
}

String _normalizeComparableText(String value) {
  return value.trim().replaceAll(RegExp(r'\s+'), ' ').toLowerCase();
}

Map<String, dynamic>? _asStringMap(dynamic value) {
  if (value is! Map) {
    return null;
  }
  return value.map((key, nestedValue) => MapEntry(key.toString(), nestedValue));
}

List<String> _stringList(dynamic value) {
  if (value is! List) {
    return const <String>[];
  }
  return value
      .map((item) => item?.toString().trim() ?? '')
      .where((item) => item.isNotEmpty)
      .toList(growable: false);
}

String? _firstText(Iterable<dynamic> values) {
  for (final value in values) {
    final text = value?.toString().trim() ?? '';
    if (text.isNotEmpty) {
      return text;
    }
  }
  return null;
}

int? _asInt(dynamic value) {
  if (value is int) {
    return value;
  }
  if (value is num) {
    return value.toInt();
  }
  return int.tryParse(value?.toString() ?? '');
}
