import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ui/core/router/go_router_manager.dart';
import 'package:ui/l10n/l10n.dart';
import 'package:ui/services/workspace_memory_service.dart';
import 'package:ui/theme/app_colors.dart';
import 'package:ui/theme/theme_context.dart';
import 'package:ui/utils/ui.dart';
import 'package:ui/widgets/common_app_bar.dart';
import 'package:ui/widgets/settings_section_title.dart';

class WorkspaceMemorySettingPage extends StatefulWidget {
  const WorkspaceMemorySettingPage({super.key});

  @override
  State<WorkspaceMemorySettingPage> createState() =>
      _WorkspaceMemorySettingPageState();
}

class _WorkspaceMemorySettingPageState
    extends State<WorkspaceMemorySettingPage> {
  final TextEditingController _soulController = TextEditingController();
  final TextEditingController _chatController = TextEditingController();
  final TextEditingController _memoryController = TextEditingController();

  bool _loading = true;
  bool _savingSoul = false;
  bool _savingChat = false;
  bool _savingMemory = false;
  bool _runningRollup = false;
  bool _embeddingEnabled = true;
  bool _rollupEnabled = false;
  WorkspaceMemoryEmbeddingConfig? _embeddingConfig;
  WorkspaceMemoryRollupStatus? _rollupStatus;

  /// 打开页面时读到的 MEMORY.md 全文，用于保存时判断期间是否被 agent 追加过。
  String _memorySnapshot = '';

  @override
  void initState() {
    super.initState();
    _loadAll();
  }

  @override
  void dispose() {
    _soulController.dispose();
    _chatController.dispose();
    _memoryController.dispose();
    super.dispose();
  }

  Future<void> _loadAll() async {
    setState(() => _loading = true);
    try {
      final results = await Future.wait([
        WorkspaceMemoryService.getAgentSoulSetting(),
        WorkspaceMemoryService.getChatPromptSetting(),
        WorkspaceMemoryService.getLongMemory(),
        WorkspaceMemoryService.getEmbeddingConfig(),
        WorkspaceMemoryService.getRollupStatus(),
      ]);
      if (!mounted) return;
      final soul = results[0] as String;
      final chatPrompt = results[1] as String;
      final memory = results[2] as String;
      final embedding = results[3] as WorkspaceMemoryEmbeddingConfig;
      final rollup = results[4] as WorkspaceMemoryRollupStatus;
      setState(() {
        _soulController.text = soul;
        _chatController.text = chatPrompt;
        _memoryController.text = memory;
        _memorySnapshot = memory;
        _embeddingConfig = embedding;
        _rollupStatus = rollup;
        _embeddingEnabled = embedding.enabled;
        _rollupEnabled = rollup.enabled;
      });
    } catch (e) {
      // await 期间可能已经离开这个页面，dispose 后再用 context 查
      // ScaffoldMessenger 会触发 "deactivated widget's ancestor" 断言。
      if (!mounted) return;
      showToast(context.l10n.workspaceMemoryLoadFailed, type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _saveSoul() async {
    setState(() => _savingSoul = true);
    try {
      final saved = await WorkspaceMemoryService.saveAgentSoulSetting(
        _soulController.text,
      );
      if (!mounted) return;
      _soulController.text = saved;
      showToast(context.l10n.agentSoulSaved, type: ToastType.success);
    } catch (e) {
      if (!mounted) return;
      showToast(context.l10n.agentSoulSaveFailed, type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() => _savingSoul = false);
      }
    }
  }

  Future<void> _saveChatPrompt() async {
    setState(() => _savingChat = true);
    try {
      final saved = await WorkspaceMemoryService.saveChatPromptSetting(
        _chatController.text,
      );
      if (!mounted) return;
      _chatController.text = saved;
      showToast(context.l10n.chatPromptSaved, type: ToastType.success);
    } catch (e) {
      if (!mounted) return;
      showToast(context.l10n.chatPromptSaveFailed, type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() => _savingChat = false);
      }
    }
  }

  Future<void> _saveMemory() async {
    setState(() => _savingMemory = true);
    try {
      // MEMORY.md 会被 agent 的 memory_upsert_longterm 和夜间 rollup 追加写入，
      // 而这里写回的是「打开页面时读到的全文」——直接整篇覆盖会把期间新增的条目抹掉。
      final latest = await WorkspaceMemoryService.getLongMemory();
      if (!mounted) return;
      final edited = _memoryController.text;
      if (latest != _memorySnapshot) {
        if (edited == _memorySnapshot) {
          // 用户没改动，只是点了保存：直接用最新内容刷新，不做写回。
          _memorySnapshot = latest;
          setState(() => _memoryController.text = latest);
          showToast(context.l10n.workspaceMemorySaved, type: ToastType.success);
          return;
        }
        final overwrite = await _confirmMemoryOverwrite();
        if (!mounted) return;
        if (!overwrite) {
          _memorySnapshot = latest;
          setState(() => _memoryController.text = latest);
          return;
        }
      }
      final saved = await WorkspaceMemoryService.saveLongMemory(edited);
      if (!mounted) return;
      _memorySnapshot = saved;
      _memoryController.text = saved;
      showToast(context.l10n.workspaceMemorySaved, type: ToastType.success);
    } catch (e) {
      if (!mounted) return;
      showToast(context.l10n.workspaceMemorySaveFailed, type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() => _savingMemory = false);
      }
    }
  }

  /// 期间长期记忆被 agent 追加过：让用户选「重新加载」还是「覆盖保存」。
  Future<bool> _confirmMemoryOverwrite() async {
    final isEnglish = Localizations.localeOf(context).languageCode == 'en';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(isEnglish ? 'Memory was updated' : '记忆已更新'),
        content: Text(
          isEnglish
              ? 'Long-term memory changed while this page was open (the agent or the nightly rollup appended entries). Saving now overwrites those additions.'
              : '本页面打开期间长期记忆被追加了新内容（agent 或夜间整理）。现在保存会覆盖这些新增条目。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(isEnglish ? 'Reload' : '重新加载'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(isEnglish ? 'Overwrite' : '覆盖保存'),
          ),
        ],
      ),
    );
    return confirmed ?? false;
  }

  Future<void> _toggleEmbedding(bool enabled) async {
    setState(() => _embeddingEnabled = enabled);
    try {
      final config = await WorkspaceMemoryService.saveEmbeddingConfig(
        enabled: enabled,
      );
      if (!mounted) return;
      setState(() => _embeddingConfig = config);
    } catch (e) {
      if (!mounted) return;
      setState(() => _embeddingEnabled = !enabled);
      showToast(
        context.l10n.workspaceEmbeddingToggleFailed,
        type: ToastType.error,
      );
    }
  }

  Future<void> _toggleRollup(bool enabled) async {
    setState(() => _rollupEnabled = enabled);
    try {
      final status = await WorkspaceMemoryService.saveRollupEnabled(enabled);
      if (!mounted) return;
      setState(() => _rollupStatus = status);
    } catch (e) {
      if (!mounted) return;
      setState(() => _rollupEnabled = !enabled);
      showToast(
        context.l10n.workspaceRollupToggleFailed,
        type: ToastType.error,
      );
    }
  }

  Future<void> _runRollupNow() async {
    // 没有 in-flight 守卫时连点会并发触发多次 rollup。
    if (_runningRollup) return;
    setState(() => _runningRollup = true);
    try {
      final result = await WorkspaceMemoryService.runRollupNow();
      if (!mounted) return;
      showToast(
        (result?['summary'] ?? context.l10n.workspaceRollupDone).toString(),
      );
      await _loadAll();
    } on PlatformException catch (e) {
      // 两个 catch 分支原来直接用 context.l10n —— 页面已 dispose 时查
      // ScaffoldMessenger 会断言失败（本文件 75 行注释里自己警告过这一点）。
      if (!mounted) return;
      final message = e.message?.trim();
      final errorText = (message == null || message.isEmpty)
          ? context.l10n.workspaceRollupFailed
          : '${context.l10n.workspaceRollupFailed}：$message';
      showToast(errorText, type: ToastType.error);
    } catch (e) {
      if (!mounted) return;
      showToast(
        '${context.l10n.workspaceRollupFailed}：$e',
        type: ToastType.error,
      );
    } finally {
      if (mounted) {
        setState(() => _runningRollup = false);
      }
    }
  }

  String _formatTime(int? millis) {
    if (millis == null || millis <= 0) return context.l10n.workspaceNone;
    final date = DateTime.fromMillisecondsSinceEpoch(millis);
    return '${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')} '
        '${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Scaffold(
      backgroundColor: palette.pageBackground,
      appBar: CommonAppBar(
        title: context.l10n.workspaceMemoryTitle,
        primary: true,
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: edgeToEdgeScrollPadding(
                context,
                const EdgeInsets.fromLTRB(18, 12, 18, 28),
              ),
              children: [
                SettingsSectionTitle(
                  label: context.l10n.workspaceMemoryCapability,
                ),
                _buildSwitchCard(
                  title: context.l10n.workspaceEmbeddingRetrieval,
                  subtitle: _embeddingConfig?.configured == true
                      ? context.l10n.workspaceEmbeddingReady
                      : context.l10n.workspaceEmbeddingNotReady,
                  value: _embeddingEnabled,
                  onChanged: _toggleEmbedding,
                  footer: _embeddingConfig?.usesPlatform == true
                      ? null
                      : TextButton(
                          onPressed: () {
                            GoRouterManager.push('/home/scene_model_setting');
                          },
                          child: Text(context.l10n.workspaceGoToConfig),
                        ),
                ),
                const Divider(height: 24),
                _buildSwitchCard(
                  title: context.l10n.workspaceNightlyRollup,
                  subtitle:
                      '${context.l10n.workspaceLastRun(_formatTime(_rollupStatus?.lastRunAtMillis))}\n${context.l10n.workspaceNextRun(_formatTime(_rollupStatus?.nextRunAtMillis))}',
                  value: _rollupEnabled,
                  onChanged: _toggleRollup,
                  footer: Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton(
                      onPressed: _runRollupNow,
                      child: Text(context.l10n.workspaceRollupNow),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                SettingsSectionTitle(
                  label: context.l10n.workspaceSettingsAndMemory,
                ),
                _buildEditorCard(
                  title: context.l10n.agentSoulSetting,
                  controller: _soulController,
                  saving: _savingSoul,
                  onSave: _saveSoul,
                ),
                const Divider(height: 24),
                _buildEditorCard(
                  title: context.l10n.chatPromptSetting,
                  controller: _chatController,
                  saving: _savingChat,
                  onSave: _saveChatPrompt,
                ),
                const Divider(height: 24),
                _buildEditorCard(
                  title: context.l10n.workspaceMemoryMd,
                  controller: _memoryController,
                  saving: _savingMemory,
                  onSave: _saveMemory,
                ),
              ],
            ),
    );
  }

  Widget _buildSwitchCard({
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool> onChanged,
    Widget? footer,
  }) {
    final palette = context.omniPalette;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: context.isDarkTheme
                        ? palette.textPrimary
                        : AppColors.text,
                  ),
                ),
              ),
              Switch(value: value, onChanged: onChanged),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            subtitle,
            style: TextStyle(
              fontSize: 12,
              color: context.isDarkTheme
                  ? palette.textSecondary
                  : AppColors.text70,
            ),
          ),
          if (footer != null) ...[const SizedBox(height: 8), footer],
        ],
      ),
    );
  }

  Widget _buildEditorCard({
    required String title,
    required TextEditingController controller,
    required bool saving,
    required Future<void> Function() onSave,
  }) {
    final palette = context.omniPalette;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w600,
              color: context.isDarkTheme ? palette.textPrimary : AppColors.text,
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: controller,
            maxLines: 12,
            minLines: 8,
            decoration: InputDecoration(
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
              ),
            ),
          ),
          const SizedBox(height: 10),
          Align(
            alignment: Alignment.centerRight,
            child: ElevatedButton(
              onPressed: saving ? null : onSave,
              child: saving
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(context.trLegacy('保存')),
            ),
          ),
        ],
      ),
    );
  }
}
