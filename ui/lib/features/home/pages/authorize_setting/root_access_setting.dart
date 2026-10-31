import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:ui/services/root_access_service.dart';
import 'package:ui/theme/theme_context.dart';
import 'package:ui/utils/ui.dart';
import 'package:ui/widgets/common_app_bar.dart';

/// Root 后端设置页。
///
/// 上游这版是用他们的 `OmniSettingsTile` / `OmniSettingsSwitch`（settings_ui.dart）
/// 写的，本分支没有那套组件，这里用 Material 等价控件重写，状态机与文案保持一致。
class RootAccessSetting extends StatefulWidget {
  const RootAccessSetting({super.key});

  @override
  State<RootAccessSetting> createState() => _RootAccessSettingState();
}

class _RootAccessSettingState extends State<RootAccessSetting>
    with WidgetsBindingObserver {
  RootAccessSnapshot _snapshot = const RootAccessSnapshot();
  bool _loaded = false;
  bool _busy = false;
  bool _failed = false;
  int _revision = 0;
  Timer? _refreshTimer;

  bool get _english => Localizations.localeOf(context).languageCode == 'en';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !_busy) _refresh();
  }

  Future<void> _refresh() async {
    if (_busy) return;
    _refreshTimer?.cancel();
    final revision = _revision;
    try {
      final snapshot = await RootAccessService.status();
      if (!mounted || revision != _revision) return;
      setState(() {
        _snapshot = snapshot;
        _loaded = true;
        _failed = false;
      });
      if (snapshot.checking) {
        _refreshTimer = Timer(const Duration(milliseconds: 500), _refresh);
      }
    } catch (_) {
      if (mounted && revision == _revision) setState(() => _failed = true);
    }
  }

  Future<void> _toggle(bool enabled) async {
    if (enabled) {
      if (_busy) return;
      final confirmed = await AppDialog.confirm(
        context,
        title: _english ? 'Enable Root access' : '启用 Root 高级能力',
        content: _english
            ? 'Allow the agent to use Root shell and system controls. Privileged commands and changes still require your confirmation. You can disable access at any time.'
            : '允许 Agent 使用 Root shell 和系统控制能力。高权限命令与状态修改仍需逐次确认，你可以随时关闭。',
        cancelText: _english ? 'Cancel' : '取消',
        confirmText: _english ? 'Enable and authorize' : '启用并授权',
      );
      if (confirmed != true || !mounted) return;
    }
    await _perform(
      () => RootAccessService.setEnabled(enabled),
      pending: RootAccessSnapshot(
        enabled: enabled,
        status: enabled ? 'CHECKING' : 'DISABLED',
      ),
    );
  }

  Future<void> _perform(
    Future<RootAccessSnapshot> Function() operation, {
    required RootAccessSnapshot pending,
  }) async {
    final revision = ++_revision;
    setState(() {
      _snapshot = pending;
      _busy = true;
      _failed = false;
    });
    try {
      final result = await operation();
      if (mounted && revision == _revision) {
        setState(() {
          _snapshot = result;
          _loaded = true;
        });
      }
    } catch (_) {
      if (mounted && revision == _revision) {
        setState(() => _busy = false);
        await _refresh();
        if (mounted && revision == _revision) setState(() => _failed = true);
      }
    } finally {
      if (mounted && revision == _revision) setState(() => _busy = false);
    }
  }

  String get _subtitle {
    if (_failed) {
      return _english
          ? 'Could not read access status. Retry below.'
          : '无法读取授权状态，请重试。';
    }
    final hint = _english
        ? 'Optional system controls and Root shell. Requests access only after you enable it.'
        : '可选的系统控制与 Root shell；主动开启后才申请授权。';
    return '${_snapshot.label(_english)}\n$hint';
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final canToggle = _snapshot.enabled || (_loaded && !_busy && !_failed);
    return Scaffold(
      backgroundColor: palette.pageBackground,
      appBar: CommonAppBar(
        title: _english ? 'Root access' : 'Root 权限',
        primary: true,
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(8, 8, 8, 28),
        children: [
          SwitchListTile.adaptive(
            key: const ValueKey('root-access-switch'),
            value: _snapshot.enabled,
            onChanged: canToggle ? _toggle : null,
            secondary: Icon(
              LucideIcons.shield,
              size: 22,
              color: palette.textSecondary,
            ),
            title: Text(
              _english ? 'Root access' : 'Root 高级能力',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: palette.textPrimary,
              ),
            ),
            subtitle: Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                _subtitle,
                style: TextStyle(
                  fontSize: 12.5,
                  height: 1.45,
                  color: palette.textSecondary,
                ),
              ),
            ),
          ),
          if (_failed ||
              (_snapshot.enabled && !_snapshot.granted && !_snapshot.checking))
            ListTile(
              key: const ValueKey('root-access-retry'),
              leading: Icon(
                LucideIcons.refreshCw,
                size: 20,
                color: palette.textSecondary,
              ),
              title: Text(
                _failed
                    ? (_english ? 'Refresh access status' : '刷新授权状态')
                    : (_english ? 'Retry Root authorization' : '重新申请 Root 授权'),
                style: TextStyle(fontSize: 14, color: palette.textPrimary),
              ),
              onTap: _busy
                  ? null
                  : () {
                      if (_failed) {
                        _refresh();
                      } else {
                        _perform(
                          RootAccessService.requestAccess,
                          pending: const RootAccessSnapshot(
                            enabled: true,
                            status: 'CHECKING',
                          ),
                        );
                      }
                    },
            ),
          if (_busy)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: LinearProgressIndicator(minHeight: 2),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            child: Text(
              _english
                  ? 'Authorization is stored on this device only and is excluded from system backups. Turning access off stops any running privileged session.'
                  : '授权只保存在本机，且排除在系统备份之外。关闭后正在运行的特权会话会被一并结束。',
              style: TextStyle(
                fontSize: 12,
                height: 1.5,
                color: palette.textTertiary,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
