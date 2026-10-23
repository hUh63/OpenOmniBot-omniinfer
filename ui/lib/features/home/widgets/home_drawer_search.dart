// ignore_for_file: invalid_use_of_protected_member

part of 'home_drawer.dart';

extension _HomeDrawerSearch on HomeDrawerState {
  bool get _isSearchActive => _searchQuery.isNotEmpty;

  String get _searchQuery => _searchController.text.trim();

  List<_ConversationSearchResult> get _visibleConversationResults {
    if (_isSearchActive) {
      return _searchResults;
    }
    final reservedThreadKeys = _promotedConversationThreadKeys;
    return _allConversations
        .where(
          (conversation) =>
              !conversation.isArchived &&
              !conversation.isPinned &&
              !reservedThreadKeys.contains(conversation.threadKey),
        )
        .map(
          (conversation) =>
              _ConversationSearchResult(conversation: conversation),
        )
        .toList(growable: false);
  }

  Set<String> get _promotedConversationThreadKeys {
    final groups = _scheduledConversationGroups;
    final cached = _promotedThreadKeysCache;
    if (cached != null && identical(_promotedThreadKeysCacheSource, groups)) {
      return cached;
    }
    final keys = <String>{};
    for (final group in groups) {
      keys.add(group.parent.threadKey);
      for (final child in group.children) {
        keys.add(child.conversation.threadKey);
      }
    }
    _promotedThreadKeysCacheSource = groups;
    _promotedThreadKeysCache = keys;
    return keys;
  }

  List<_ConversationSearchResult> get _pinnedConversationResults {
    final reservedThreadKeys = _promotedConversationThreadKeys;
    return _allConversations
        .where(
          (conversation) =>
              !conversation.isArchived &&
              conversation.isPinned &&
              !reservedThreadKeys.contains(conversation.threadKey),
        )
        .map(
          (conversation) =>
              _ConversationSearchResult(conversation: conversation),
        )
        .toList(growable: false);
  }

  List<_ScheduledConversationGroup> get _scheduledConversationGroups {
    final cached = _scheduledGroupsCache;
    if (cached != null &&
        identical(_scheduledGroupsCacheConversations, _allConversations) &&
        identical(_scheduledGroupsCacheTasks, _scheduledTasks)) {
      return cached;
    }
    final groups = _computeScheduledConversationGroups();
    _scheduledGroupsCacheConversations = _allConversations;
    _scheduledGroupsCacheTasks = _scheduledTasks;
    _scheduledGroupsCache = groups;
    return groups;
  }

  List<_ScheduledConversationGroup> _computeScheduledConversationGroups() {
    final visibleConversations = _allConversations
        .where((conversation) => !conversation.isArchived)
        .toList(growable: false);
    if (visibleConversations.isEmpty) {
      return const <_ScheduledConversationGroup>[];
    }

    final conversationsByKey = <String, ConversationModel>{
      for (final conversation in visibleConversations)
        conversation.threadKey: conversation,
    };
    final parentKeys = <String>{};
    final activeScheduledTaskIds = <String>{};
    final activeParentKeys = <String>{};
    final taskCountByParentKey = <String, int>{};

    for (final task in _scheduledTasks) {
      if (task.targetKind != 'subagent') {
        continue;
      }
      activeScheduledTaskIds.add(task.id);
      final parentKey = _scheduledTaskParentThreadKey(task, conversationsByKey);
      if (parentKey == null || !conversationsByKey.containsKey(parentKey)) {
        continue;
      }
      activeParentKeys.add(parentKey);
      parentKeys.add(parentKey);
      taskCountByParentKey[parentKey] =
          (taskCountByParentKey[parentKey] ?? 0) + 1;
    }

    final childrenByParentKey = <String, List<_ConversationSearchResult>>{};
    for (final conversation in visibleConversations) {
      final parentKey = _conversationParentThreadKey(
        conversation,
        conversationsByKey,
      );
      if (parentKey == null || !conversationsByKey.containsKey(parentKey)) {
        continue;
      }
      final scheduledTaskId = (conversation.scheduledTaskId ?? '').trim();
      final belongsToActiveScheduledTask = scheduledTaskId.isNotEmpty
          ? activeScheduledTaskIds.contains(scheduledTaskId)
          : activeParentKeys.contains(parentKey);
      if (!belongsToActiveScheduledTask) {
        continue;
      }
      parentKeys.add(parentKey);
      childrenByParentKey
          .putIfAbsent(parentKey, () => <_ConversationSearchResult>[])
          .add(_ConversationSearchResult(conversation: conversation));
    }

    final groups = <_ScheduledConversationGroup>[];
    for (final parentKey in parentKeys) {
      final parent = conversationsByKey[parentKey];
      if (parent == null) {
        continue;
      }
      final children =
          childrenByParentKey[parentKey] ?? <_ConversationSearchResult>[];
      children.sort((a, b) {
        final byUpdatedAt = b.conversation.updatedAt.compareTo(
          a.conversation.updatedAt,
        );
        if (byUpdatedAt != 0) return byUpdatedAt;
        return b.conversation.createdAt.compareTo(a.conversation.createdAt);
      });
      groups.add(
        _ScheduledConversationGroup(
          parent: parent,
          children: children,
          taskCount: taskCountByParentKey[parentKey] ?? 0,
        ),
      );
    }

    groups.sort((a, b) {
      final aLatest = _scheduledGroupLatestTimestamp(a);
      final bLatest = _scheduledGroupLatestTimestamp(b);
      final byLatest = bLatest.compareTo(aLatest);
      if (byLatest != 0) return byLatest;
      return b.parent.createdAt.compareTo(a.parent.createdAt);
    });
    return groups;
  }

  String? _scheduledTaskParentThreadKey(
    ScheduledTask task,
    Map<String, ConversationModel> conversationsByKey,
  ) {
    final rawParentId =
        (task.parentConversationId ?? task.subagentConversationId ?? '').trim();
    final parentId = int.tryParse(rawParentId);
    if (parentId == null || parentId <= 0) {
      return null;
    }
    if (task.parentConversationMode == null ||
        task.parentConversationMode!.trim().isEmpty) {
      return _threadKeyForConversationId(parentId, conversationsByKey);
    }
    final parentMode = ConversationMode.fromStorageValue(
      task.parentConversationMode,
    );
    return '${parentMode.storageValue}:$parentId';
  }

  String? _conversationParentThreadKey(
    ConversationModel conversation,
    Map<String, ConversationModel> conversationsByKey,
  ) {
    final parentId = conversation.parentConversationId;
    if (parentId == null || parentId <= 0) {
      return null;
    }
    final parentMode = conversation.parentConversationMode;
    if (parentMode == null) {
      return _threadKeyForConversationId(parentId, conversationsByKey);
    }
    return '${parentMode.storageValue}:$parentId';
  }

  String? _threadKeyForConversationId(
    int conversationId,
    Map<String, ConversationModel> conversationsByKey,
  ) {
    for (final conversation in conversationsByKey.values) {
      if (conversation.id == conversationId) {
        return conversation.threadKey;
      }
    }
    return null;
  }

  int _scheduledGroupLatestTimestamp(_ScheduledConversationGroup group) {
    var latest = group.parent.updatedAt;
    for (final child in group.children) {
      if (child.conversation.updatedAt > latest) {
        latest = child.conversation.updatedAt;
      }
    }
    return latest;
  }

  Future<void> _loadConversations() async {
    final generation = ++_conversationLoadGeneration;
    debugPrint('[HomeDrawer] Loading conversations...');
    setState(() {
      isLoadingConversations =
          _allConversations.isEmpty &&
          !HomeDrawerState._hasConversationSnapshotCache;
    });

    try {
      // 首屏先拿最近一页：会话很多的账号不必等全量传输 + 解析完才出列表。
      // 随后仍会拉全量（快照缓存、日程分组、图片预览都依赖完整列表），
      // 所以这里是「先出图、后补齐」，不改变任何最终语义。
      if (isLoadingConversations) {
        try {
          final firstPage = await ConversationService
              .getSidebarConversationsPage(limit: 40);
          if (!mounted || generation != _conversationLoadGeneration) return;
          if (firstPage.isNotEmpty) {
            setState(() {
              _allConversations = firstPage;
              isLoadingConversations = false;
            });
          }
        } catch (e) {
          debugPrint('[HomeDrawer] First page load failed: $e');
        }
      }
      final loadedConversations =
          await ConversationService.getSidebarConversations();
      final loadedScheduledTasks =
          await ScheduledTaskStorageService.loadScheduledTasks();
      debugPrint(
        '[HomeDrawer] Loaded ${loadedConversations.length} conversations',
      );
      if (!mounted || generation != _conversationLoadGeneration) return;
      final visibleThreadKeys = loadedConversations
          .map((conversation) => conversation.threadKey)
          .toSet();
      _conversationSearchCache.removeWhere(
        (threadKey, _) => !visibleThreadKeys.contains(threadKey),
      );
      _pruneConversationImagePreviewState(visibleThreadKeys);
      if (!mounted || generation != _conversationLoadGeneration) return;
      setState(() {
        _allConversations = loadedConversations;
        _scheduledTasks = loadedScheduledTasks;
        isLoadingConversations = false;
      });
      _rememberConversationSnapshotCache(loadedConversations);
      _scheduleDrawerSnapshotPersist(loadedConversations);
      _refreshConversationImagePreviewsInBackground(
        loadedConversations
            .where((conversation) => !conversation.isArchived)
            // 只预热最近十几条：其余行首次可见时按需加载。此前对全部未归档
            // 会话逐条拉完整消息，会话越多抽屉越卡。
            .take(HomeDrawerState._imagePreviewWarmupCount)
            .toList(growable: false),
        generation: generation,
      );
      if (_isSearchActive) {
        _scheduleConversationSearch(immediate: true);
      }
    } catch (e) {
      debugPrint('[HomeDrawer] Failed to load conversations: $e');
      if (!mounted || generation != _conversationLoadGeneration) return;
      setState(() {
        isLoadingConversations = false;
      });
    }
  }

  void _handleSearchFocusChanged() {
    widget.onSearchFocusChanged?.call(_searchFocusNode.hasFocus);
  }

  void _handleSearchQueryChanged() {
    if (!mounted) {
      return;
    }
    _searchGeneration += 1;
    _searchDebounceTimer?.cancel();

    if (_searchQuery.isEmpty) {
      setState(() {
        _searchResults = <_ConversationSearchResult>[];
        _isSearching = false;
      });
      return;
    }

    setState(() {});
    _scheduleConversationSearch();
  }

  void _scheduleConversationSearch({bool immediate = false}) {
    final query = _searchQuery;
    if (query.isEmpty) {
      return;
    }

    final generation = _searchGeneration;
    _searchDebounceTimer?.cancel();
    void callback() {
      unawaited(_performConversationSearch(query, generation: generation));
    }

    if (immediate) {
      callback();
      return;
    }
    _searchDebounceTimer = Timer(
      HomeDrawerState._searchDebounceDuration,
      callback,
    );
  }

  Future<void> _performConversationSearch(
    String query, {
    required int generation,
  }) async {
    final trimmedQuery = query.trim();
    if (trimmedQuery.isEmpty || generation != _searchGeneration) {
      return;
    }

    final queryTokens = _tokenizeSearchQuery(trimmedQuery);
    if (queryTokens.isEmpty) {
      if (!mounted || generation != _searchGeneration) {
        return;
      }
      setState(() {
        _searchResults = <_ConversationSearchResult>[];
        _isSearching = false;
      });
      return;
    }

    if (mounted) {
      setState(() {
        _isSearching = true;
      });
    }

    // 搜索交给后端 SQL（标题 / 摘要 / 上下文摘要 / 最后一条消息）。
    // 原实现在 Dart 侧遍历全部会话、对每条不命中的会话再去拉「全部消息」建索引，
    // 代价是 O(会话数 × 全部消息)，输入一次可能要跑很久。
    final List<ConversationModel> matched;
    try {
      matched = await ConversationService.searchConversations(
        trimmedQuery,
        limit: 80,
      );
    } catch (error) {
      // 没有 try/catch 时异常会让 _isSearching 永远停在 true（抽屉一直显示
      // 搜索中），并且残留上一次的关键字结果，只有清空输入才能恢复。
      debugPrint('[HomeDrawer] search failed: $error');
      if (!mounted || generation != _searchGeneration) {
        return;
      }
      setState(() {
        _searchResults = <_ConversationSearchResult>[];
        _isSearching = false;
      });
      return;
    }
    if (!mounted || generation != _searchGeneration) {
      return;
    }

    final results = <_ConversationSearchResult>[
      for (final conversation in matched)
        _ConversationSearchResult(
          conversation: conversation,
          matchedPreview:
              _resolveMatchedPreview(
                candidates: _buildConversationMetadataCandidates(conversation),
                conversation: conversation,
                queryTokens: queryTokens,
              ) ??
              _fallbackSearchPreview(conversation),
        ),
    ];

    if (!mounted || generation != _searchGeneration) {
      return;
    }
    setState(() {
      _searchResults = results;
      _isSearching = false;
    });
  }

  Future<_ConversationSearchResult?> _matchConversationAgainstQuery(
    ConversationModel conversation,
    List<String> queryTokens,
  ) async {
    final metadataCandidates = _buildConversationMetadataCandidates(
      conversation,
    );
    if (_matchesSearchTokens(
      _normalizeSearchText(metadataCandidates.join('\n')),
      queryTokens,
    )) {
      return _ConversationSearchResult(
        conversation: conversation,
        matchedPreview: _resolveMatchedPreview(
          candidates: metadataCandidates,
          conversation: conversation,
          queryTokens: queryTokens,
        ),
      );
    }

    final searchIndex = await _ensureConversationSearchIndex(conversation);
    if (!_matchesSearchTokens(searchIndex.searchableText, queryTokens)) {
      return null;
    }

    return _ConversationSearchResult(
      conversation: conversation,
      matchedPreview: _resolveMatchedPreview(
        candidates: searchIndex.candidates,
        conversation: conversation,
        queryTokens: queryTokens,
      ),
    );
  }

  Future<_ConversationSearchIndex> _ensureConversationSearchIndex(
    ConversationModel conversation,
  ) async {
    final signature = _conversationSearchSignature(conversation);
    final cacheKey = conversation.threadKey;
    final cached = _conversationSearchCache[cacheKey];
    if (cached != null && cached.signature == signature) {
      return cached;
    }

    final candidates = _buildConversationMetadataCandidates(conversation);
    final seenCandidates = candidates
        .map(_normalizeSearchText)
        .where((value) => value.isNotEmpty)
        .toSet();
    final messages = await ConversationHistoryService.getConversationMessages(
      conversation.id,
      mode: conversation.mode,
      expectedMessageCount: conversation.messageCount,
    );

    for (final message in messages) {
      for (final fragment in _collectSearchableText(message)) {
        _addUniqueCandidate(candidates, seenCandidates, fragment);
      }
    }

    final searchIndex = _ConversationSearchIndex(
      signature: signature,
      candidates: List<String>.unmodifiable(candidates),
      searchableText: _normalizeSearchText(candidates.join('\n')),
    );
    _conversationSearchCache[cacheKey] = searchIndex;
    return searchIndex;
  }

  List<String> _buildConversationMetadataCandidates(
    ConversationModel conversation,
  ) {
    final candidates = <String>[];
    final seenCandidates = <String>{};
    _addUniqueCandidate(
      candidates,
      seenCandidates,
      _resolveConversationTitle(conversation),
    );
    _addUniqueCandidate(candidates, seenCandidates, conversation.summary);
    _addUniqueCandidate(
      candidates,
      seenCandidates,
      conversation.contextSummary,
    );
    _addUniqueCandidate(candidates, seenCandidates, conversation.lastMessage);
    return candidates;
  }

  List<String> _collectSearchableText(ChatMessageModel message) {
    final fragments = <String>[];
    final seenCandidates = <String>{};
    _collectSearchableTextFromValue(
      message.content,
      sink: fragments,
      seenNormalized: seenCandidates,
    );
    return fragments;
  }

  void _collectSearchableTextFromValue(
    dynamic value, {
    required List<String> sink,
    required Set<String> seenNormalized,
  }) {
    if (value == null) {
      return;
    }
    if (value is String) {
      _addUniqueCandidate(sink, seenNormalized, value);
      return;
    }
    if (value is List) {
      for (final item in value) {
        _collectSearchableTextFromValue(
          item,
          sink: sink,
          seenNormalized: seenNormalized,
        );
      }
      return;
    }
    if (value is Map) {
      for (final item in value.values) {
        _collectSearchableTextFromValue(
          item,
          sink: sink,
          seenNormalized: seenNormalized,
        );
      }
    }
  }

  void _addUniqueCandidate(
    List<String> candidates,
    Set<String> seenNormalized,
    String? rawValue,
  ) {
    final normalized = _normalizeSearchText(rawValue ?? '');
    if (normalized.isEmpty || !seenNormalized.add(normalized)) {
      return;
    }
    candidates.add((rawValue ?? '').replaceAll(RegExp(r'\s+'), ' ').trim());
  }

  List<String> _tokenizeSearchQuery(String value) {
    return _normalizeSearchText(value)
        .split(RegExp(r'\s+'))
        .where((token) => token.isNotEmpty)
        .toList(growable: false);
  }

  String _normalizeSearchText(String value) {
    return value.replaceAll(RegExp(r'\s+'), ' ').trim().toLowerCase();
  }

  bool _matchesSearchTokens(String searchableText, List<String> queryTokens) {
    if (searchableText.isEmpty || queryTokens.isEmpty) {
      return false;
    }
    return queryTokens.every(searchableText.contains);
  }

  /// 正文命中（SQL 直接匹配消息内容）时，元数据里可能没有可展示的命中片段，
  /// 用最后一条消息开头补一段，避免「搜到了但看不出为什么匹配」。
  String? _fallbackSearchPreview(ConversationModel conversation) {
    final raw = (conversation.lastMessage ?? conversation.summary ?? '').trim();
    if (raw.isEmpty) {
      return null;
    }
    final normalized = raw.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (normalized.isEmpty) {
      return null;
    }
    const maxLength = 80;
    return normalized.length <= maxLength
        ? normalized
        : '${normalized.substring(0, maxLength)}…';
  }

  String? _resolveMatchedPreview({
    required List<String> candidates,
    required ConversationModel conversation,
    required List<String> queryTokens,
  }) {
    final title = _resolveConversationTitle(conversation);
    for (final candidate in candidates) {
      final normalized = _normalizeSearchText(candidate);
      if (_matchesSearchTokens(normalized, queryTokens) &&
          candidate.trim() != title) {
        return candidate.trim();
      }
    }
    for (final candidate in candidates) {
      final normalized = _normalizeSearchText(candidate);
      if (queryTokens.any(normalized.contains) && candidate.trim() != title) {
        return candidate.trim();
      }
    }
    return null;
  }

  String _conversationSearchSignature(ConversationModel conversation) {
    return [
      conversation.threadKey,
      conversation.updatedAt,
      conversation.messageCount,
      conversation.isArchived ? 1 : 0,
      conversation.isPinned ? 1 : 0,
      conversation.parentConversationId ?? '',
      conversation.parentConversationMode?.storageValue ?? '',
      conversation.scheduledTaskId ?? '',
      conversation.title,
      conversation.summary ?? '',
      conversation.contextSummary ?? '',
      conversation.lastMessage ?? '',
    ].join('|');
  }
}
