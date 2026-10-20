import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ui/models/conversation_model.dart';
import 'package:ui/models/conversation_thread_target.dart';
import 'package:ui/services/agent_runtime_service.dart';
import 'package:ui/services/conversation_history_service.dart';
import 'package:ui/services/storage_service.dart';

class ConversationService {
  static const Duration recentConversationWindow = Duration(days: 7);
  static const MethodChannel _assistCore = MethodChannel(
    'cn.com.omnimind.bot/AssistCoreEvent',
  );
  static const String _hiddenAgentConversationIdsKey =
      'hidden_agent_conversation_ids';
  static const String _legacyHiddenAgentConversationIdsKey =
      'hidden_codex_conversation_ids';
  static final StreamController<bool> _sidebarPolicyChangedController =
      StreamController<bool>.broadcast();

  static Stream<bool> get sidebarPolicyChangedStream =>
      _sidebarPolicyChangedController.stream;

  static bool isRecentConversationsOnlyEnabled() =>
      StorageService.isRecentConversationsOnlyEnabled();

  static Future<bool> setRecentConversationsOnlyEnabled(bool enabled) async {
    final saved = await StorageService.setBool(
      StorageService.kRecentConversationsOnlyEnabledKey,
      enabled,
    );
    if (saved) {
      _sidebarPolicyChangedController.add(enabled);
    }
    return saved;
  }

  static int recentConversationCutoff({DateTime? now}) {
    return (now ?? DateTime.now())
        .subtract(recentConversationWindow)
        .millisecondsSinceEpoch;
  }

  static List<ConversationModel> _normalizeConversations(List<dynamic> raw) {
    final conversations = raw
        .whereType<Map>()
        .map(
          (json) => ConversationModel.fromJson(
            Map<String, dynamic>.from(json.cast<String, dynamic>()),
          ),
        )
        .toList();
    conversations.sort((a, b) {
      final byUpdatedAt = b.updatedAt.compareTo(a.updatedAt);
      if (byUpdatedAt != 0) return byUpdatedAt;
      final aPenalty = a.mode == ConversationMode.subagent ? 1 : 0;
      final bPenalty = b.mode == ConversationMode.subagent ? 1 : 0;
      final byMode = aPenalty.compareTo(bPenalty);
      if (byMode != 0) return byMode;
      return b.createdAt.compareTo(a.createdAt);
    });
    return conversations;
  }

  // 启动时抽屉、聊天页、统计卡片等会各自请求一次会话列表。同一参数的在飞请求
  // 共享同一次原生查询，避免同一时刻把整张表序列化并跨 channel 传好几遍。
  static final Map<String, Future<List<ConversationModel>>>
  _inFlightConversationLists = <String, Future<List<ConversationModel>>>{};

  static Future<List<ConversationModel>> getAllConversations({
    bool includeArchived = false,
    bool archivedOnly = false,
    int? archiveBefore,
  }) {
    final key = '$includeArchived|$archivedOnly|${archiveBefore ?? -1}';
    final inFlight = _inFlightConversationLists[key];
    if (inFlight != null) {
      return inFlight;
    }
    late final Future<List<ConversationModel>> future;
    future = _fetchConversations(
      includeArchived: includeArchived,
      archivedOnly: archivedOnly,
      archiveBefore: archiveBefore,
    ).whenComplete(() {
      if (identical(_inFlightConversationLists[key], future)) {
        _inFlightConversationLists.remove(key);
      }
    });
    _inFlightConversationLists[key] = future;
    return future;
  }

  static Future<List<ConversationModel>> _fetchConversations({
    required bool includeArchived,
    required bool archivedOnly,
    required int? archiveBefore,
  }) async {
    try {
      // 原生不回调 result 时（协程失效 / handler 在 result 之前抛错）await 会永久
      // 挂起，所有依赖列表的页面都会停在 loading。服务层兜一个超时，按“拿不到数据”
      // 处理；页面仍可各自用更短的超时给出更快的错误态。
      final result = await _assistCore
          .invokeMethod<List<dynamic>>('getConversations', <String, dynamic>{
            'includeArchived': includeArchived,
            'archivedOnly': archivedOnly,
            if (archiveBefore != null) 'archiveBefore': archiveBefore,
          })
          .timeout(const Duration(seconds: 20));
      if (result == null) return [];
      final conversations = await _filterHiddenAgentConversations(
        _normalizeConversations(result),
      );
      if (archivedOnly) {
        return conversations.where((item) => item.isArchived).toList();
      }
      if (includeArchived) {
        return conversations;
      }
      return conversations.where((item) => !item.isArchived).toList();
    } on PlatformException catch (e) {
      debugPrint('[ConversationService] 获取对话列表失败: ${e.message}');
      return [];
    } catch (e) {
      debugPrint('[ConversationService] 获取对话列表异常: $e');
      return [];
    }
  }

  static Future<List<ConversationModel>> getSidebarConversations({
    DateTime? now,
  }) {
    final recentOnly = isRecentConversationsOnlyEnabled();
    return getAllConversations(
      includeArchived: !recentOnly,
      archiveBefore: recentOnly ? recentConversationCutoff(now: now) : null,
    );
  }

  static List<ConversationModel> filterSidebarSnapshot(
    List<ConversationModel> conversations, {
    DateTime? now,
  }) {
    if (!isRecentConversationsOnlyEnabled()) {
      return List<ConversationModel>.from(conversations);
    }
    final cutoff = recentConversationCutoff(now: now);
    return conversations
        .where(
          (conversation) =>
              !conversation.isArchived && conversation.updatedAt >= cutoff,
        )
        .toList(growable: false);
  }

  static Future<List<ConversationModel>> getConversationsByPage({
    required int offset,
    required int limit,
    bool includeArchived = false,
    bool archivedOnly = false,
  }) async {
    final all = await getAllConversations(
      includeArchived: includeArchived,
      archivedOnly: archivedOnly,
    );
    if (all.isEmpty) return [];
    final start = offset < 0 ? 0 : offset;
    if (start >= all.length) return [];
    final end = (start + limit) > all.length ? all.length : (start + limit);
    return all.sublist(start, end);
  }

  static Future<int?> createConversation({
    required String title,
    String? summary,
    ConversationMode mode = ConversationMode.agent,
    String? agentId,
    int? parentConversationId,
    ConversationMode? parentConversationMode,
    String? scheduledTaskId,
    bool rethrowOnError = false,
  }) async {
    try {
      final result = await _assistCore
          .invokeMethod<dynamic>('createConversation', {
            'title': title,
            'summary': summary,
            'mode': mode.storageValue,
            if (agentId != null && agentId.trim().isNotEmpty)
              'agentId': agentId.trim(),
            if (parentConversationId != null && parentConversationId > 0)
              'parentConversationId': parentConversationId,
            if (parentConversationMode != null)
              'parentConversationMode': parentConversationMode.storageValue,
            if (scheduledTaskId != null && scheduledTaskId.trim().isNotEmpty)
              'scheduledTaskId': scheduledTaskId.trim(),
          });
      if (result is int) return result;
      if (result is String) return int.tryParse(result);
      return null;
    } on PlatformException catch (e) {
      debugPrint('创建对话失败: ${e.message}');
      if (rethrowOnError) rethrow;
      return null;
    } catch (e) {
      debugPrint('创建对话失败: $e');
      if (rethrowOnError) rethrow;
      return null;
    }
  }

  static Future<bool> updateConversation(
    ConversationModel conversation, {
    bool preserveLatestMetadata = false,
  }) async {
    try {
      final payload = preserveLatestMetadata
          ? await _mergeLatestConversationMetadata(conversation)
          : conversation;
      final result = await _assistCore.invokeMethod<dynamic>(
        'updateConversation',
        {'conversation': payload.toJson()},
      );
      return result == 'SUCCESS';
    } on PlatformException catch (e) {
      debugPrint('更新对话失败: ${e.message}');
      return false;
    } catch (e) {
      debugPrint('更新对话失败: $e');
      return false;
    }
  }

  static Future<ConversationModel> _mergeLatestConversationMetadata(
    ConversationModel conversation,
  ) async {
    final latest = await _getConversationById(
      conversation.id,
      includeArchived: true,
    );
    if (latest == null || latest.mode != conversation.mode) {
      return conversation;
    }
    return ConversationModel(
      id: conversation.id,
      mode: conversation.mode,
      agentCwd: latest.agentCwd ?? conversation.agentCwd,
      agentId: latest.agentId ?? conversation.agentId,
      isArchived: latest.isArchived,
      isPinned: latest.isPinned,
      parentConversationId: latest.parentConversationId,
      parentConversationMode: latest.parentConversationMode,
      scheduledTaskId: latest.scheduledTaskId,
      title: latest.title.isNotEmpty ? latest.title : conversation.title,
      summary: conversation.summary ?? latest.summary,
      contextSummary: latest.contextSummary,
      contextSummaryCutoffEntryDbId: latest.contextSummaryCutoffEntryDbId,
      contextSummaryUpdatedAt: latest.contextSummaryUpdatedAt,
      status: conversation.status,
      lastMessage: conversation.lastMessage,
      messageCount: conversation.messageCount,
      latestPromptTokens: latest.latestPromptTokens,
      promptTokenThreshold: latest.promptTokenThreshold,
      latestPromptTokensUpdatedAt: latest.latestPromptTokensUpdatedAt,
      createdAt: latest.createdAt,
      updatedAt: conversation.updatedAt,
    );
  }

  static Future<bool> updateConversationPromptTokenThreshold({
    required int conversationId,
    required int promptTokenThreshold,
  }) async {
    try {
      final result = await _assistCore
          .invokeMethod<dynamic>('updateConversationPromptTokenThreshold', {
            'conversationId': conversationId,
            'promptTokenThreshold': promptTokenThreshold,
          });
      return result == 'SUCCESS';
    } on PlatformException catch (e) {
      debugPrint('更新对话压缩阈值失败: ${e.message}');
      return false;
    } catch (e) {
      debugPrint('更新对话压缩阈值失败: $e');
      return false;
    }
  }

  static Future<bool> deleteConversation(
    int conversationId, {
    ConversationMode? mode,
  }) async {
    if (mode == ConversationMode.agent) {
      final runtimeArchived = await _setAgentSessionArchivedBestEffort(
        conversationId: conversationId,
        archived: true,
      );
      final conversation = await _getConversationById(
        conversationId,
        includeArchived: true,
      );
      var localArchived = conversation == null;
      if (conversation != null) {
        localArchived =
            conversation.isArchived ||
            await updateConversation(conversation.copyWith(isArchived: true));
      }
      if (!runtimeArchived && !localArchived) {
        return false;
      }
      await _markAgentConversationHidden(conversationId);
      await ConversationHistoryService.clearConversationThreadReferences(
        conversationId,
        mode: ConversationMode.agent,
      );
      await setCurrentConversationTarget(
        await ConversationHistoryService.getLastVisibleThreadTarget(),
      );
      return true;
    }
    try {
      final result = await _assistCore.invokeMethod<dynamic>(
        'deleteConversation',
        {
          'conversationId': conversationId,
          if (mode != null) 'mode': mode.storageValue,
        },
      );
      final deleted = result == 'SUCCESS';
      if (!deleted) {
        return false;
      }
      await ConversationHistoryService.clearConversationMessages(
        conversationId,
        mode: mode ?? ConversationMode.agent,
      );
      await ConversationHistoryService.clearConversationThreadReferences(
        conversationId,
        mode: mode,
      );
      await setCurrentConversationTarget(
        await ConversationHistoryService.getLastVisibleThreadTarget(),
      );
      return true;
    } on PlatformException catch (e) {
      debugPrint('删除对话失败: ${e.message}');
      return false;
    } catch (e) {
      debugPrint('删除对话失败: $e');
      return false;
    }
  }

  static Future<bool> archiveConversation(
    ConversationModel conversation,
  ) async {
    var agentArchived = false;
    if (conversation.mode == ConversationMode.agent) {
      agentArchived = await _setAgentSessionArchivedBestEffort(
        conversationId: conversation.id,
        archived: true,
      );
    }
    final archived = await updateConversation(
      conversation.copyWith(isArchived: true),
    );
    if (!archived && !agentArchived) {
      return false;
    }
    if (conversation.mode == ConversationMode.agent) {
      await ConversationHistoryService.clearConversationThreadReferences(
        conversation.id,
        mode: ConversationMode.agent,
      );
    }
    await setCurrentConversationTarget(
      await ConversationHistoryService.getLastVisibleThreadTarget(),
    );
    return true;
  }

  static Future<bool> unarchiveConversation(
    ConversationModel conversation,
  ) async {
    var agentRestored = false;
    if (conversation.mode == ConversationMode.agent) {
      agentRestored = await _setAgentSessionArchivedBestEffort(
        conversationId: conversation.id,
        archived: false,
      );
    }
    final localRestored = await updateConversation(
      conversation.copyWith(isArchived: false),
    );
    return localRestored || agentRestored;
  }

  static Future<bool> _setAgentSessionArchivedBestEffort({
    required int conversationId,
    required bool archived,
  }) async {
    try {
      if (archived) {
        await AgentRuntimeService.archiveSession(
          conversationId: conversationId,
        );
      } else {
        await AgentRuntimeService.unarchiveSession(
          conversationId: conversationId,
        );
      }
      return true;
    } catch (e) {
      final action = archived ? '归档' : '恢复';
      debugPrint('Agent session $action 同步失败，继续使用本地历史状态: $e');
      return false;
    }
  }

  /// 单条会话元数据。
  ///
  /// 之前所有调用方都是先 `getAllConversations()` 再线性查找：会话越多越慢，
  /// 而且要把整张表的全字段跨 channel 传一遍。原生侧已有单条查询，这里优先用它，
  /// 接口不可用（旧原生 / 异常）时回退全量查找，语义保持一致。
  static Future<ConversationModel?> getConversationById(
    int conversationId, {
    bool includeArchived = true,
    ConversationMode? mode,
  }) async {
    if (conversationId <= 0) {
      return null;
    }
    try {
      final raw = await _assistCore.invokeMethod<dynamic>(
        'getConversation',
        <String, dynamic>{'conversationId': conversationId},
      ).timeout(const Duration(seconds: 10));
      if (raw is Map) {
        final conversation = ConversationModel.fromJson(
          Map<String, dynamic>.from(raw),
        );
        if (!includeArchived && conversation.isArchived) {
          return null;
        }
        if (mode != null && conversation.mode != mode) {
          return null;
        }
        // 与列表接口保持一致：被隐藏的 Agent 会话对调用方不可见。
        if (conversation.mode == ConversationMode.agent) {
          final hiddenIds = await _getHiddenAgentConversationIds();
          if (hiddenIds.contains(conversation.id)) {
            return null;
          }
        }
        return conversation;
      }
    } catch (e) {
      debugPrint('[ConversationService] 单条会话查询失败，回退全量查找: $e');
    }
    final conversations = await getAllConversations(
      includeArchived: includeArchived,
    );
    for (final conversation in conversations) {
      if (conversation.id == conversationId &&
          (mode == null || conversation.mode == mode)) {
        return conversation;
      }
    }
    return null;
  }

  static Future<ConversationModel?> _getConversationById(
    int conversationId, {
    bool includeArchived = false,
  }) {
    return getConversationById(
      conversationId,
      includeArchived: includeArchived,
    );
  }

  static Future<List<ConversationModel>> _filterHiddenAgentConversations(
    List<ConversationModel> conversations,
  ) async {
    if (conversations.isEmpty) {
      return conversations;
    }
    final hiddenIds = await _getHiddenAgentConversationIds();
    if (hiddenIds.isEmpty) {
      return conversations;
    }
    return conversations
        .where(
          (conversation) =>
              conversation.mode != ConversationMode.agent ||
              !hiddenIds.contains(conversation.id),
        )
        .toList();
  }

  // 隐藏 Agent 会话的 id 每次列表加载、每次单条会话查询都要用，而它是
  // SharedPreferences（platform channel）读取。缓存在内存，写入时同步更新。
  static Set<int>? _hiddenAgentConversationIdsCache;

  static Future<Set<int>> _getHiddenAgentConversationIds() async {
    final cached = _hiddenAgentConversationIdsCache;
    if (cached != null) {
      return cached;
    }
    try {
      final prefs = await SharedPreferences.getInstance();
      final ids = <String>{
        ...?prefs.getStringList(_hiddenAgentConversationIdsKey),
        ...?prefs.getStringList(_legacyHiddenAgentConversationIdsKey),
      }.map(int.tryParse).whereType<int>().toSet();
      _hiddenAgentConversationIdsCache = ids;
      return ids;
    } catch (e) {
      debugPrint('[ConversationService] 读取 Agent 隐藏会话失败: $e');
      return const <int>{};
    }
  }

  static Future<void> _markAgentConversationHidden(int conversationId) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      // 拷贝一份再改，避免直接修改返回的缓存集合。
      final hiddenIds = <int>{...await _getHiddenAgentConversationIds()};
      if (!hiddenIds.add(conversationId)) {
        return;
      }
      final encoded = hiddenIds.map((id) => id.toString()).toList()..sort();
      await prefs.setStringList(_hiddenAgentConversationIdsKey, encoded);
      _hiddenAgentConversationIdsCache = hiddenIds;
    } catch (e) {
      debugPrint('[ConversationService] 保存 Agent 隐藏会话失败: $e');
    }
  }

  static Future<bool> updateConversationTitle({
    required int conversationId,
    required String newTitle,
    ConversationMode mode = ConversationMode.agent,
  }) async {
    var acpSessionRenamed = false;
    if (mode == ConversationMode.agent) {
      try {
        await AgentRuntimeService.setSessionName(
          conversationId: conversationId,
          name: newTitle,
        );
        acpSessionRenamed = true;
      } catch (e) {
        // Pre-ACP Xiaowan conversations can have durable history without an
        // ACP session binding. Rename the durable Conversation row below so
        // those conversations remain fully manageable; a future session/load
        // can materialize their ACP session on demand.
        debugPrint('更新 ACP 会话标题失败，回退对话标题: $e');
      }
    }
    try {
      final result = await _assistCore
          .invokeMethod<dynamic>('updateConversationTitle', {
            'conversationId': conversationId,
            'newTitle': newTitle,
            'mode': mode.storageValue,
          });
      return result == 'SUCCESS' || acpSessionRenamed;
    } on PlatformException catch (e) {
      debugPrint('更新对话标题失败: ${e.message}');
      return acpSessionRenamed;
    } catch (e) {
      debugPrint('更新对话标题失败: $e');
      return acpSessionRenamed;
    }
  }

  static Future<String?> generateConversationSummary({
    required String conversationHistory,
  }) async {
    try {
      final result = await _assistCore.invokeMethod(
        'generateConversationSummary',
        {'conversationHistory': conversationHistory},
      );
      return result as String?;
    } on PlatformException catch (e) {
      debugPrint('生成对话摘要失败: ${e.message}');
      return null;
    } catch (e) {
      debugPrint('生成对话摘要失败: $e');
      return null;
    }
  }

  static Future<bool> completeConversation(
    int conversationId, {
    ConversationMode? mode,
  }) async {
    try {
      final result = await _assistCore.invokeMethod<dynamic>(
        'completeConversation',
        {
          'conversationId': conversationId,
          if (mode != null) 'mode': mode.storageValue,
        },
      );
      return result == 'SUCCESS';
    } on PlatformException catch (e) {
      debugPrint('完成对话失败: ${e.message}');
      return false;
    } catch (e) {
      debugPrint('完成对话失败: $e');
      return false;
    }
  }

  static Future<bool> setCurrentConversationId(
    int? conversationId, {
    ConversationMode mode = ConversationMode.agent,
  }) async {
    try {
      final result = await _assistCore.invokeMethod<dynamic>(
        'setCurrentConversationId',
        {'conversationId': conversationId ?? 0, 'mode': mode.storageValue},
      );
      return result == 'SUCCESS';
    } on PlatformException catch (e) {
      debugPrint('设置当前对话ID失败: ${e.message}');
      return false;
    } catch (e) {
      debugPrint('设置当前对话ID失败: $e');
      return false;
    }
  }

  static Future<bool> setCurrentConversationTarget(
    ConversationThreadTarget? target,
  ) async {
    return setCurrentConversationId(
      target?.conversationId,
      mode: target?.mode ?? ConversationMode.agent,
    );
  }

  static Future<ConversationModel?> getLatestConversation({
    ConversationMode? mode,
    bool includeArchived = false,
  }) async {
    final conversations = await getAllConversations(
      includeArchived: includeArchived,
    );
    for (final conversation in conversations) {
      if (mode == null || conversation.mode == mode) {
        return conversation;
      }
    }
    return null;
  }

  static Future<ConversationThreadTarget?> getLatestConversationTarget({
    ConversationMode? mode,
    bool includeArchived = false,
  }) async {
    final conversation = await getLatestConversation(
      mode: mode,
      includeArchived: includeArchived,
    );
    if (conversation == null) {
      return null;
    }
    return ConversationThreadTarget.existing(
      conversationId: conversation.id,
      mode: conversation.mode,
      agentId: conversation.agentId,
    );
  }
}
