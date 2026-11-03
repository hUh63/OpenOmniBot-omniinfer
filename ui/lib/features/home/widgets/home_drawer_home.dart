// ignore_for_file: invalid_use_of_protected_member

part of 'home_drawer.dart';

class _HomeConversationFilter {
  const _HomeConversationFilter({
    required this.id,
    this.agentId,
    this.name,
    this.mode = ConversationMode.agent,
  });

  static const all = _HomeConversationFilter(id: 'all');
  static const chat = _HomeConversationFilter(
    id: 'chat',
    mode: ConversationMode.chatOnly,
  );
  static const xiaowan = _HomeConversationFilter(
    id: 'xiaowan-acp',
    agentId: 'xiaowan-acp',
    name: '小万',
  );

  final String id;
  final String? agentId;
  final String? name;
  final ConversationMode mode;
}

extension _HomeDrawerHome on HomeDrawerState {
  Future<void> _loadHomeAgentCatalog() =>
      runPageLoad(#homeAgentCatalog, _readHomeAgentCatalog, revalidate: true);

  Future<void> _readHomeAgentCatalog() async {
    final request = ++_homeCatalogRequest;
    try {
      final catalog = await AgentRuntimeService.listAgents();
      if (!await waitForPageReady() ||
          request != _homeCatalogRequest ||
          !pageLoadIsCurrent(#homeAgentCatalog)) {
        return;
      }
      setState(() {
        _homeAgentCatalog = catalog;
        final filters = _homeFilters;
        // Refresh the name as well as availability without resetting a valid
        // selection. An unavailable Agent falls back to the first usable one.
        _homeFilter =
            filters
                .where((filter) => filter.id == _homeFilter.id)
                .firstOrNull ??
            filters.firstWhere(
              (filter) => filter.agentId != null,
              orElse: () => _HomeConversationFilter.chat,
            );
      });
    } catch (error) {
      // Retain the last catalog during a transient native refresh failure.
      // Xiaowan remains available through the same built-in fallback as chat.
      debugPrint('Load home Agent catalog failed: $error');
    }
  }

  List<_HomeConversationFilter> get _homeFilters {
    final profiles = _homeAgentCatalog?.agents ?? const <AcpAgentProfile>[];
    final available = profiles
        .where(
          (profile) =>
              profile.enabled &&
              profile.installed == true &&
              profile.status.trim().toLowerCase() != 'missing',
        )
        .toList(growable: false);
    return [
      _HomeConversationFilter.all,
      if (!profiles.any((profile) => profile.id == 'xiaowan-acp'))
        _HomeConversationFilter.xiaowan,
      // Filtering availability preserves the shared native catalog order.
      for (final profile in available)
        _HomeConversationFilter(
          id: profile.id,
          agentId: profile.id,
          name: profile.name.trim().isEmpty ? profile.id : profile.name.trim(),
        ),
      _HomeConversationFilter.chat,
    ];
  }

  bool _matchesHomeFilter(ConversationModel conversation) {
    if (!widget.homePage) return true;
    if (_homeFilter.id == 'all') return true;
    if (_homeFilter.mode == ConversationMode.chatOnly) {
      return conversation.mode == ConversationMode.chatOnly;
    }
    if (conversation.mode == ConversationMode.chatOnly ||
        conversation.mode == ConversationMode.openclaw) {
      return false;
    }
    // Old Xiaowan and scheduled child conversations predate agentId.
    final savedAgentId = conversation.agentId?.trim() ?? '';
    final agentId = savedAgentId.isEmpty ? 'xiaowan-acp' : savedAgentId;
    return AgentBrandIcon.normalizeAgentId(agentId) ==
        AgentBrandIcon.normalizeAgentId(_homeFilter.agentId ?? '');
  }

  Widget _buildHomePage() {
    final palette = context.omniPalette;
    return PopScope(
      canPop:
          !widget.active || (!_homeSearchVisible && !_isSelectingConversations),
      onPopInvokedWithResult: (didPop, _) {
        if (didPop || !widget.active) return;
        if (_isSelectingConversations) {
          _exitConversationSelection();
        } else if (_homeSearchVisible) {
          _closeHomeSearch();
        }
      },
      child: Scaffold(
        backgroundColor: palette.pageBackground,
        resizeToAvoidBottomInset: true,
        body: SafeArea(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (_isSelectingConversations)
                _buildConversationSelectionHeader(),
              if (!_isSelectingConversations || _homeSearchVisible)
                HomeSearchHeader(
                  searching: _homeSearchVisible,
                  updateIndicator: _buildHomeAppUpdateIndicator(),
                  searchFieldKey: widget.searchFieldKey,
                  controller: _searchController,
                  focusNode: _searchFocusNode,
                  isSearching: _isSearching,
                  onSearch: _openHomeSearch,
                  onClose: _closeHomeSearch,
                  menuButton: _buildHomeMenuButton(),
                ),
              const SizedBox(height: 10),
              if (!_isSelectingConversations) ...[
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 22),
                  child: _homeSearchVisible
                      ? _buildHomeSearchContext()
                      : _buildHomeAgentSwitcher(compact: true),
                ),
                const SizedBox(height: 10),
              ],
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 22),
                  child: SlidableAutoCloseBehavior(
                    child: isLoadingConversations
                        ? _buildConversationLoadingState()
                        : _isSearchActive
                        ? _buildSearchConversationBody(
                            _visibleConversationResults,
                          )
                        : _buildConversationTimelineBody(
                            _visibleConversationResults,
                          ),
                  ),
                ),
              ),
            ],
          ),
        ),
        bottomNavigationBar: _isSelectingConversations
            ? _buildConversationSelectionBar()
            : null,
        floatingActionButton: _isSelectingConversations
            ? null
            : Padding(
                padding: const EdgeInsets.only(right: 6, bottom: 24),
                child: SizedBox(
                  width: 52,
                  height: 52,
                  child: FloatingActionButton(
                    key: const ValueKey('home-new-conversation'),
                    heroTag: 'home-new-conversation',
                    tooltip: context.l10n.homeDrawerNewChat,
                    onPressed: _openNewConversation,
                    backgroundColor: palette.accentPrimary,
                    foregroundColor: Theme.of(context).colorScheme.onPrimary,
                    shape: const CircleBorder(),
                    elevation: 3,
                    child: const Icon(LucideIcons.plus, size: 26),
                  ),
                ),
              ),
      ),
    );
  }

  void _openHomeSearch() {
    if (_homeSearchVisible) return;
    setState(() => _homeSearchVisible = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _homeSearchVisible) _searchFocusNode.requestFocus();
    });
  }

  Widget _buildHomeAppUpdateIndicator() {
    return ValueListenableBuilder<AppUpdateStatus?>(
      valueListenable: AppUpdateService.statusNotifier,
      builder: (context, status, _) {
        if (!AppUpdateService.shouldShowBanner(status)) {
          return const SizedBox.shrink();
        }
        final updateStatus = status!;
        final english = Localizations.localeOf(context).languageCode == 'en';
        return Padding(
          padding: const EdgeInsets.only(right: 4),
          child: IconButton(
            key: const ValueKey('home-app-update-button'),
            tooltip: english
                ? 'New version ${updateStatus.latestVersionLabel} available'
                : '发现新版本 ${updateStatus.latestVersionLabel}',
            onPressed: () => showAppUpdateDialog(context, updateStatus),
            padding: const EdgeInsets.all(15),
            constraints: const BoxConstraints.tightFor(width: 48, height: 48),
            icon: SvgPicture.asset(
              'assets/home/chat/update_sparkles.svg',
              width: 18,
              height: 18,
              colorFilter: const ColorFilter.mode(
                Color(0xFFD4A017),
                BlendMode.srcIn,
              ),
            ),
          ),
        );
      },
    );
  }

  void _closeHomeSearch() {
    _searchController.clear();
    _searchFocusNode.unfocus();
    setState(() => _homeSearchVisible = false);
  }

  String _homeFilterLabel(_HomeConversationFilter filter) {
    final english = Localizations.localeOf(context).languageCode == 'en';
    return switch (filter.id) {
      'all' => english ? 'All' : '全部',
      'chat' => english ? 'Chat' : '纯聊天',
      'xiaowan-acp' => english ? 'Xiaowan' : '小万',
      _ => filter.name ?? filter.id,
    };
  }

  Widget _buildHomeFilterIcon(_HomeConversationFilter filter, double size) {
    return SizedBox.square(
      dimension: size,
      child: Center(
        child: filter.agentId != null
            ? AgentBrandIcon(agentId: filter.agentId!, size: size)
            : Icon(
                filter.id == 'all'
                    ? LucideIcons.layers
                    : LucideIcons.messageCircle,
                size: size,
                color: context.omniPalette.textPrimary,
              ),
      ),
    );
  }

  /// Search keeps the current filter visible without moving the history list.
  Widget _buildHomeSearchContext() => ConstrainedBox(
    constraints: const BoxConstraints(minHeight: 48),
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Row(
        children: [
          ExcludeSemantics(child: _buildHomeFilterIcon(_homeFilter, 24)),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              _homeFilterLabel(_homeFilter),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w600,
                height: 1.2,
                color: context.omniPalette.textSecondary,
              ),
            ),
          ),
        ],
      ),
    ),
  );

  Widget _buildHomeAgentSwitcher({bool compact = false}) {
    final palette = context.omniPalette;
    final english = Localizations.localeOf(context).languageCode == 'en';
    // Match the popup reveal's initial 24dp corners so the trigger's ink stays
    // within the menu throughout its expansion.
    const triggerRadius = BorderRadius.all(Radius.circular(24));
    // Keep the header's overflow glyph fully visible beside the overlay.
    final menuWidth = (MediaQuery.sizeOf(context).width - 84).clamp(0.0, 340.0);
    return Builder(
      builder: (anchorContext) => Tooltip(
        message: english ? 'Switch Agent' : '切换 Agent',
        child: Material(
          color: Colors.transparent,
          borderRadius: triggerRadius,
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            key: const ValueKey('home-agent-switcher'),
            borderRadius: triggerRadius,
            onTap: _isBatchConversationActionRunning
                ? null
                : () async {
                    final anchor = glassPopupAnchorFromContext(anchorContext);
                    if (anchor == null) return;
                    performOmniSelectionHaptic();
                    final id = await showOmniMenu<String>(
                      context: anchorContext,
                      anchor: anchor,
                      width: menuWidth,
                      items: [
                        for (final filter in _homeFilters)
                          OmniPopupMenuItem<String>(
                            key: ValueKey('home-agent-option-${filter.id}'),
                            value: filter.id,
                            selected: _homeFilter.id == filter.id,
                            child: Row(
                              children: [
                                ExcludeSemantics(
                                  child: _buildHomeFilterIcon(
                                    filter,
                                    OmniMenuStyle.iconSize,
                                  ),
                                ),
                                const SizedBox(width: OmniMenuStyle.iconGap),
                                Expanded(
                                  child: Text(
                                    _homeFilterLabel(filter),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                                const SizedBox(width: 12),
                                SizedBox.square(
                                  dimension: 18,
                                  child: _homeFilter.id == filter.id
                                      ? const Icon(LucideIcons.check, size: 18)
                                      : null,
                                ),
                              ],
                            ),
                          ),
                      ],
                    );
                    // The catalog can change while the menu is open. Select only
                    // a current entry using the existing conversation filter.
                    if (!mounted || _isBatchConversationActionRunning) return;
                    final filter = _homeFilters
                        .where((item) => item.id == id)
                        .firstOrNull;
                    if (filter != null) _selectHomeFilter(filter);
                  },
            child: Semantics(
              button: true,
              enabled: !_isBatchConversationActionRunning,
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      ExcludeSemantics(
                        child: _buildHomeFilterIcon(
                          _homeFilter,
                          compact ? 24 : 28,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Flexible(
                        child: LayoutBuilder(
                          builder: (context, constraints) {
                            final name = Text(
                              _homeFilterLabel(_homeFilter),
                              key: const ValueKey('home-agent-name'),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontSize: compact ? 17 : 22,
                                fontWeight: FontWeight.w600,
                                color: palette.textPrimary,
                                height: 1.2,
                              ),
                            );
                            final badge = DecoratedBox(
                              decoration: BoxDecoration(
                                color: palette.brandSurface,
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 4,
                                  vertical: 2,
                                ),
                                child: Text(
                                  english ? 'Switch' : '切换',
                                  key: const ValueKey(
                                    'home-agent-switch-action',
                                  ),
                                  maxLines: 1,
                                  softWrap: false,
                                  style: TextStyle(
                                    fontSize: compact ? 10 : 11,
                                    fontWeight: FontWeight.w600,
                                    color: palette.brandPrimary,
                                    height: 1.2,
                                  ),
                                ),
                              ),
                            );
                            final inlineWidth = MediaQuery.textScalerOf(context)
                                .scale(english ? 92 : 78);
                            if (constraints.maxWidth < inlineWidth) {
                              // Keep the badge above-right of a readable name
                              // when large text cannot share the same line.
                              return Column(
                                mainAxisSize: MainAxisSize.min,
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                  Align(
                                    alignment: Alignment.centerRight,
                                    child: badge,
                                  ),
                                  name,
                                ],
                              );
                            }
                            return Row(
                              mainAxisSize: MainAxisSize.min,
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Flexible(child: name),
                                const SizedBox(width: 4),
                                badge,
                              ],
                            );
                          },
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _selectHomeFilter(_HomeConversationFilter filter) {
    if (_homeFilter.id == filter.id) return;
    performOmniSelectionHaptic();
    setState(() => _homeFilter = filter);
  }

  List<_DrawerShortcutAction> get _homeMenuActions => [
    _DrawerShortcutAction(
      label: context.l10n.homeDrawerArchive,
      assetPath: 'assets/home/archive_icon.svg',
      onTap: () => _navigateTo('/home/archived_conversations'),
    ),
    ..._shortcutActions,
    if (_webQuickActions.isNotEmpty)
      _DrawerShortcutAction(
        label: 'Agent WebUI',
        icon: LucideIcons.globe,
        onTap: _showHomeWebActions,
      ),
  ];

  Widget _buildHomeMenuButton() => Builder(
    builder: (buttonContext) => FlClashPopupBox<VoidCallback>(
      onOpened: () {
        scheduleMicrotask(() {
          if (buttonContext.mounted) {
            HomeSearchHeader.releaseMenuPressOf(buttonContext);
          }
        });
        // Show the cached actions on pointer down, then refresh the already-open
        // menu. Plugin I/O must not hold the source button's opening animation.
        unawaited(_loadWebQuickActions());
      },
      onSelected: (action) {
        if (mounted) action();
      },
      targetBuilder: (context, open) => IconButton(
        key: const ValueKey('home-menu-button'),
        tooltip: MaterialLocalizations.of(context).showMenuTooltip,
        onPressed: open,
        icon: const Icon(LucideIcons.ellipsis),
      ),
      popupBuilder: (popupContext) => ValueListenableBuilder<int>(
        valueListenable: _homeMenuRevision,
        builder: (context, _, _) {
          if (!mounted) return const SizedBox.shrink();
          final actions = _homeMenuActions;
          return SizedBox(
            width: 260,
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(8),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (var i = 0; i < actions.length; i++)
                    FlClashPopupMenuItem<VoidCallback>(
                      key: ValueKey('home-menu-item-$i'),
                      value: actions[i].onTap,
                      child: Row(
                        children: [
                          _shortcutIcon(actions[i], size: 20),
                          const SizedBox(width: 14),
                          Expanded(child: Text(actions[i].label)),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          );
        },
      ),
    ),
  );

  Future<void> _showHomeWebActions() async {
    await _refreshWebQuickActionStatuses();
    if (!mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(sheetContext).height * 0.7,
          ),
          child: ListView(
            shrinkWrap: true,
            padding: OmniMenuStyle.panelPadding,
            children: [
              for (final action in _webQuickActions)
                _buildWebPopoverOptionTile(
                  sheetContext,
                  action,
                  surface: sheetContext.omniPalette.surfacePrimary,
                  english: Localizations.localeOf(context).languageCode == 'en',
                ),
            ],
          ),
        ),
      ),
    );
  }
}
