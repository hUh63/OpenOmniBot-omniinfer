part of 'chat_widgets.dart';

/// A presentation command. Its callback continues through the existing owner.
class ChatAppBarAction {
  const ChatAppBarAction({
    required this.label,
    required this.icon,
    required this.onSelected,
  });
  final String label;
  final IconData icon;
  final FutureOr<void> Function(BuildContext anchorContext) onSelected;
}

enum ChatSurfaceMode { workspace, normal, openclaw }

class ChatAcpAgentModeOption {
  const ChatAcpAgentModeOption({
    required this.id,
    required this.name,
    this.enabled = true,
    this.installed = true,
    this.status = 'online',
  });

  final String id;
  final String name;
  final bool enabled;
  final bool installed;
  final String status;

  bool get isAvailable =>
      enabled && installed && status.trim().toLowerCase() != 'missing';
}

/// 聊天页面 AppBar
class ChatAppBar extends StatelessWidget {
  static const _navigationButtonSize = 40.0;

  final VoidCallback onMenuTap;
  final String? title;
  final List<ChatAppBarAction> actions;
  final Key? tutorialActionsAnchorKey;
  final VoidCallback? onPetTap;
  final bool isPetOpening;
  final bool isPetShowing;
  final VoidCallback? onPrimaryModeTap;
  final ChatSurfaceMode activeMode;
  final ValueChanged<ChatSurfaceMode> onModeChanged;
  final ChatIslandDisplayLayer displayLayer;
  final ValueChanged<ChatIslandDisplayLayer> onDisplayLayerChanged;
  final ValueChanged<BuildContext> onTerminalEnvironmentTap;
  final VoidCallback onTerminalTap;
  final VoidCallback onBrowserTap;
  final bool hasTerminalEnvironment;
  final bool isBrowserEnabled;
  final bool showTerminalActions;
  final String? activeToolType;
  final List<ChatAcpAgentModeOption> acpAgentModes;
  final String? activeAcpAgentId;
  final bool showAppUpdateIndicator;
  final VoidCallback? onAppUpdateTap;
  final String? appUpdateTooltip;
  final bool translucent;
  final AppBackgroundVisualProfile visualProfile;
  final bool showMenuButton;
  final bool showSurfaceSwitcher;
  final bool isPureChatSelected;
  final bool showWorkspacePaneButton;
  final VoidCallback? onWorkspacePaneTap;
  final Key? tutorialMenuAnchorKey;
  final Key? tutorialModeAnchorKey;

  const ChatAppBar({
    super.key,
    required this.onMenuTap,
    this.title,
    this.actions = const [],
    this.tutorialActionsAnchorKey,
    this.onPetTap,
    this.isPetOpening = false,
    this.isPetShowing = false,
    this.onPrimaryModeTap,
    required this.activeMode,
    required this.onModeChanged,
    this.displayLayer = ChatIslandDisplayLayer.mode,
    required this.onDisplayLayerChanged,
    required this.onTerminalEnvironmentTap,
    required this.onTerminalTap,
    required this.onBrowserTap,
    this.hasTerminalEnvironment = false,
    this.isBrowserEnabled = false,
    this.showTerminalActions = true,
    this.activeToolType,
    this.acpAgentModes = const <ChatAcpAgentModeOption>[],
    this.activeAcpAgentId,
    this.showAppUpdateIndicator = false,
    this.onAppUpdateTap,
    this.appUpdateTooltip,
    this.translucent = false,
    this.visualProfile = AppBackgroundVisualProfile.defaultProfile,
    this.showMenuButton = true,
    this.showSurfaceSwitcher = true,
    this.isPureChatSelected = false,
    this.showWorkspacePaneButton = false,
    this.onWorkspacePaneTap,
    this.tutorialMenuAnchorKey,
    this.tutorialModeAnchorKey,
  });

  String _modeLabel(bool english) {
    if (isPureChatSelected) return english ? 'Pure chat' : '纯聊天';
    for (final agent in acpAgentModes) {
      if (agent.id == activeAcpAgentId) return agent.name;
    }
    final id = activeAcpAgentId?.trim();
    if (id == 'na-cloud') return 'Na';
    if (id == 'openclaw') return 'OpenClaw';
    if (id != null &&
        id.isNotEmpty &&
        AgentBrandIcon.normalizeAgentId(id) != 'xiaowan-acp') {
      return id;
    }
    return english ? 'OmniBot Agent' : '小万 Agent';
  }

  Future<void> _openActionsMenu(BuildContext anchorContext) async {
    final english = Localizations.localeOf(anchorContext).languageCode == 'en';
    final items = <ChatAppBarAction>[
      ...actions,
      ChatAppBarAction(
        label: activeMode == ChatSurfaceMode.workspace
            ? (english ? 'Return to conversation' : '返回对话')
            : (english ? 'Workspace files' : '工作区文件'),
        icon: LucideIcons.folder,
        onSelected: (_) {
          if (!showSurfaceSwitcher && onWorkspacePaneTap != null) {
            onWorkspacePaneTap!();
          } else {
            onModeChanged(
              activeMode == ChatSurfaceMode.workspace
                  ? ChatSurfaceMode.normal
                  : ChatSurfaceMode.workspace,
            );
          }
        },
      ),
      if (showTerminalActions)
        ChatAppBarAction(
          label: english ? 'Terminal' : '终端',
          icon: LucideIcons.terminal,
          onSelected: (_) => onTerminalTap(),
        ),
      if (showTerminalActions)
        ChatAppBarAction(
          label: english ? 'Environment variables' : '环境变量',
          icon: LucideIcons.slidersHorizontal,
          onSelected: onTerminalEnvironmentTap,
        ),
      if (isBrowserEnabled)
        ChatAppBarAction(
          label: english ? 'Browser' : '浏览器',
          icon: LucideIcons.globe,
          onSelected: (_) => onBrowserTap(),
        ),
      if (onPrimaryModeTap != null)
        ChatAppBarAction(
          label: english ? 'Agent sessions' : 'Agent 会话',
          icon: LucideIcons.messagesSquare,
          onSelected: (_) => onPrimaryModeTap!(),
        ),
      if (onPetTap != null)
        ChatAppBarAction(
          label: isPetShowing
              ? (english ? 'Hide pet' : '收起宠物')
              : (english ? 'Show pet' : '唤起宠物'),
          icon: LucideIcons.pawPrint,
          onSelected: (_) {
            if (!isPetOpening) onPetTap!();
          },
        ),
      if (showAppUpdateIndicator && onAppUpdateTap != null)
        ChatAppBarAction(
          label: appUpdateTooltip ?? (english ? 'Check for updates' : '检查更新'),
          icon: LucideIcons.download,
          onSelected: (_) => onAppUpdateTap!(),
        ),
    ];
    final tapTarget = glassPopupAnchorFromContext(anchorContext);
    if (tapTarget == null) return;
    // Preserve the padded touch target, but align the menu to the visible
    // circle rather than its extra invisible hit area.
    final anchor = Rect.fromCenter(
      center: tapTarget.center,
      width: _navigationButtonSize,
      height: _navigationButtonSize,
    );
    final selected = await showOmniMenu<int>(
      context: anchorContext,
      sourceContext: anchorContext,
      anchor: anchor,
      width: 260,
      requestFocus: false,
      items: [
        for (var index = 0; index < items.length; index++)
          OmniPopupMenuItem<int>(
            value: index,
            child: Row(
              children: [
                Icon(items[index].icon, size: 20),
                const SizedBox(width: OmniMenuStyle.iconGap),
                Expanded(child: Text(items[index].label)),
              ],
            ),
          ),
      ],
    );
    if (selected != null && anchorContext.mounted) {
      await items[selected].onSelected(anchorContext);
    }
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final english = Localizations.localeOf(context).languageCode == 'en';
    final foreground = translucent
        ? visualProfile.appBarIconColor
        : palette.textPrimary;
    return TextFieldTapRegion(
      child: ColoredBox(
        key: const ValueKey('chat-app-bar-background'),
        color: translucent ? Colors.transparent : palette.pageBackground,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 4, 14, 6),
          child: Row(
            children: [
              if (showMenuButton) ...[
                KeyedSubtree(
                  key: tutorialMenuAnchorKey,
                  child: OmniCircleButton(
                    key: const ValueKey('chat-app-bar-menu-button'),
                    icon: LucideIcons.chevronLeft,
                    size: _navigationButtonSize,
                    tapTargetSize: MaterialTapTargetSize.padded,
                    tooltip: english ? 'Back to conversations' : '返回会话列表',
                    foregroundColor: palette.textPrimary,
                    onPressed: onMenuTap,
                  ),
                ),
                const SizedBox(width: 8),
              ],
              Expanded(
                child: KeyedSubtree(
                  key: tutorialModeAnchorKey,
                  child: Row(
                    children: [
                      Semantics(
                        key: const ValueKey('chat-app-bar-mode-icon'),
                        label: _modeLabel(english),
                        image: true,
                        child: ExcludeSemantics(
                          child: isPureChatSelected
                              ? Icon(
                                  LucideIcons.messageCircle,
                                  size: 20,
                                  color: foreground,
                                )
                              : AgentBrandIcon(
                                  agentId:
                                      activeAcpAgentId?.trim().isNotEmpty ==
                                          true
                                      ? activeAcpAgentId!
                                      : 'xiaowan-acp',
                                  size: 20,
                                  fallbackColor: foreground,
                                  tint: translucent ? foreground : null,
                                ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          activeMode == ChatSurfaceMode.workspace
                              ? (english ? 'Workspace' : '工作区')
                              : ((title?.trim().isNotEmpty ?? false)
                                    ? title!
                                    : (english ? 'New conversation' : '新对话')),
                          key: const ValueKey('chat-app-bar-title'),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 17,
                            height: 1.3,
                            fontWeight: FontWeight.w600,
                            color: foreground,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 8),
              KeyedSubtree(
                key: tutorialActionsAnchorKey,
                child: OmniCircleButton.popup(
                  key: const ValueKey('chat-app-bar-more-button'),
                  icon: LucideIcons.ellipsis,
                  size: _navigationButtonSize,
                  tapTargetSize: MaterialTapTargetSize.padded,
                  tooltip: english ? 'Conversation options' : '对话选项',
                  foregroundColor: palette.textPrimary,
                  onOpen: _openActionsMenu,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
