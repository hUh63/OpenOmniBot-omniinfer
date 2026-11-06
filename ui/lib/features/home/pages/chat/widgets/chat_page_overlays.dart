part of '../chat_page.dart';

class _UserMessageQuickMenuContent extends StatelessWidget {
  const _UserMessageQuickMenuContent({
    required this.width,
    required this.showEditAction,
    required this.showRetryAction,
  });

  final double width;
  final bool showEditAction;
  final bool showRetryAction;

  void _select(BuildContext context, _UserMessageQuickAction action) {
    Navigator.of(context).pop(action);
  }

  Widget _buildAction(
    BuildContext context, {
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return OmniMenuItem(onTap: onTap, label: label, leading: Icon(icon));
  }

  @override
  Widget build(BuildContext context) {
    return OmniMenuSurface(
      width: width,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildAction(
            context,
            icon: LucideIcons.copy,
            label: LegacyTextLocalizer.isEnglish ? 'Copy' : '复制',
            onTap: () => _select(context, _UserMessageQuickAction.copy),
          ),
          if (showEditAction)
            _buildAction(
              context,
              icon: LucideIcons.pencil,
              label: LegacyTextLocalizer.isEnglish ? 'Edit' : '编辑',
              onTap: () => _select(context, _UserMessageQuickAction.edit),
            ),
          if (showRetryAction)
            _buildAction(
              context,
              icon: LucideIcons.rotateCw,
              label: LegacyTextLocalizer.isEnglish ? 'Retry' : '重试这条消息',
              onTap: () => _select(context, _UserMessageQuickAction.retry),
            ),
        ],
      ),
    );
  }
}

class _PaneResizeHandle extends StatelessWidget {
  const _PaneResizeHandle({
    required this.onDragUpdate,
    this.onDragStart,
    this.onDragEnd,
  });

  final ValueChanged<double> onDragUpdate;
  final VoidCallback? onDragStart;
  final VoidCallback? onDragEnd;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onHorizontalDragStart: (_) => onDragStart?.call(),
      onHorizontalDragUpdate: (details) => onDragUpdate(details.delta.dx),
      onHorizontalDragEnd: (_) => onDragEnd?.call(),
      onHorizontalDragCancel: () => onDragEnd?.call(),
      child: SizedBox(
        width: HdPadPaneLayoutResolver.dividerHitWidth,
        child: Center(
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: context.omniPalette.borderStrong,
              borderRadius: const BorderRadius.all(Radius.circular(999)),
            ),
            child: SizedBox(width: 3, height: 52),
          ),
        ),
      ),
    );
  }
}
