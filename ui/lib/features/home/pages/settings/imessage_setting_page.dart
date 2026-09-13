import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_switch/flutter_switch.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:ui/l10n/l10n.dart';
import 'package:ui/services/im_channel_service.dart';
import 'package:ui/theme/theme_context.dart';
import 'package:ui/utils/ui.dart';
import 'package:ui/widgets/common_app_bar.dart';
import 'package:ui/widgets/settings_section_title.dart';

class ImessageSettingPage extends StatefulWidget {
  const ImessageSettingPage({super.key});

  @override
  State<ImessageSettingPage> createState() => _ImessageSettingPageState();
}

class _ImessageSettingPageState extends State<ImessageSettingPage> {
  static const Duration _autoSaveDelay = Duration(milliseconds: 900);

  final TextEditingController _telegramTokenController =
      TextEditingController();
  final TextEditingController _telegramApiBaseController =
      TextEditingController();
  final TextEditingController _telegramAllowedController =
      TextEditingController();
  final TextEditingController _telegramChunkController =
      TextEditingController();

  final TextEditingController _wechatTokenController = TextEditingController();
  final TextEditingController _wechatBaseUrlController =
      TextEditingController();
  final TextEditingController _wechatBotTypeController =
      TextEditingController();
  final TextEditingController _wechatVersionController =
      TextEditingController();
  final TextEditingController _wechatChunkController = TextEditingController();

  ImChannelState? _state;
  bool _loading = true;
  bool _savingTelegram = false;
  bool _savingWechat = false;
  bool _requestingWechatQr = false;
  bool _telegramTokenVisible = false;
  bool _wechatTokenVisible = false;
  bool _telegramAdvancedExpanded = false;
  bool _wechatAdvancedExpanded = false;
  bool _applyingState = false;
  Timer? _telegramAutoSaveTimer;
  Timer? _wechatAutoSaveTimer;

  @override
  void initState() {
    super.initState();
    _attachAutoSaveListeners();
    _loadState();
  }

  @override
  void dispose() {
    _telegramAutoSaveTimer?.cancel();
    _wechatAutoSaveTimer?.cancel();
    _telegramTokenController.dispose();
    _telegramApiBaseController.dispose();
    _telegramAllowedController.dispose();
    _telegramChunkController.dispose();
    _wechatTokenController.dispose();
    _wechatBaseUrlController.dispose();
    _wechatBotTypeController.dispose();
    _wechatVersionController.dispose();
    _wechatChunkController.dispose();
    super.dispose();
  }

  Future<void> _loadState() async {
    setState(() => _loading = true);
    final state = await ImChannelService.state();
    if (!mounted) return;
    if (state == null) {
      showToast(context.trLegacy('加载 IMessage 配置失败'), type: ToastType.error);
      setState(() => _loading = false);
      return;
    }
    _applyState(state);
    setState(() => _loading = false);
  }

  void _applyState(ImChannelState state) {
    _applyingState = true;
    try {
      _state = state;
      _telegramTokenController.text = state.telegram.botToken;
      _telegramApiBaseController.text = state.telegram.apiBaseUrl;
      _telegramAllowedController.text = state.telegram.allowedChatIds;
      _telegramChunkController.text = state.telegram.chunkSize.toString();
      _wechatTokenController.text = state.wechat.token;
      _wechatBaseUrlController.text = state.wechat.baseUrl;
      _wechatBotTypeController.text = state.wechat.botType;
      _wechatVersionController.text = state.wechat.version;
      _wechatChunkController.text = state.wechat.chunkSize.toString();
    } finally {
      _applyingState = false;
    }
  }

  void _attachAutoSaveListeners() {
    _telegramTokenController.addListener(_scheduleTelegramAutoSave);
    _telegramApiBaseController.addListener(_scheduleTelegramAutoSave);
    _telegramAllowedController.addListener(_scheduleTelegramAutoSave);
    _telegramChunkController.addListener(_scheduleTelegramAutoSave);
    _wechatTokenController.addListener(_scheduleWechatAutoSave);
    _wechatBaseUrlController.addListener(_scheduleWechatAutoSave);
    _wechatBotTypeController.addListener(_scheduleWechatAutoSave);
    _wechatVersionController.addListener(_scheduleWechatAutoSave);
    _wechatChunkController.addListener(_scheduleWechatAutoSave);
  }

  void _scheduleTelegramAutoSave() {
    if (_applyingState || _loading) return;
    _telegramAutoSaveTimer?.cancel();
    _telegramAutoSaveTimer = Timer(_autoSaveDelay, () {
      if (!mounted) return;
      unawaited(_saveTelegram(silent: true));
    });
  }

  void _scheduleWechatAutoSave() {
    if (_applyingState || _loading) return;
    _wechatAutoSaveTimer?.cancel();
    _wechatAutoSaveTimer = Timer(_autoSaveDelay, () {
      if (!mounted) return;
      unawaited(_saveWechat(silent: true));
    });
  }

  Future<void> _saveTelegram({bool? enabled, bool silent = false}) async {
    _telegramAutoSaveTimer?.cancel();
    setState(() => _savingTelegram = true);
    try {
      final current = _state?.telegram ?? TelegramImSettings.fromMap(null);
      final next = current.copyWith(
        enabled: enabled,
        botToken: _telegramTokenController.text.trim(),
        apiBaseUrl: _telegramApiBaseController.text.trim(),
        allowedChatIds: _telegramAllowedController.text.trim(),
        chunkSize: int.tryParse(_telegramChunkController.text.trim()) ?? 3900,
      );
      final state = await ImChannelService.saveTelegram(next);
      if (!mounted || state == null) return;
      setState(() {
        if (silent && !_telegramControllersMatch(next)) {
          _state = state;
        } else {
          _applyState(state);
        }
      });
      if (!silent) {
        showToast(context.trLegacy('Telegram 配置已保存'), type: ToastType.success);
      }
    } on PlatformException catch (error) {
      showToast(
        context.trLegacy(error.message ?? '保存失败'),
        type: ToastType.error,
      );
    } finally {
      if (mounted) setState(() => _savingTelegram = false);
    }
  }

  Future<void> _saveWechat({bool? enabled, bool silent = false}) async {
    _wechatAutoSaveTimer?.cancel();
    setState(() => _savingWechat = true);
    try {
      final current = _state?.wechat ?? WechatImSettings.fromMap(null);
      final next = current.copyWith(
        enabled: enabled,
        token: _wechatTokenController.text.trim(),
        baseUrl: _wechatBaseUrlController.text.trim(),
        botType: _wechatBotTypeController.text.trim(),
        version: _wechatVersionController.text.trim(),
        chunkSize: int.tryParse(_wechatChunkController.text.trim()) ?? 3000,
      );
      final state = await ImChannelService.saveWechat(next);
      if (!mounted || state == null) return;
      setState(() {
        if (silent && !_wechatControllersMatch(next)) {
          _state = state;
        } else {
          _applyState(state);
        }
      });
      if (!silent) {
        showToast(context.trLegacy('微信配置已保存'), type: ToastType.success);
      }
    } on PlatformException catch (error) {
      showToast(
        context.trLegacy(error.message ?? '保存失败'),
        type: ToastType.error,
      );
    } finally {
      if (mounted) setState(() => _savingWechat = false);
    }
  }

  Future<void> _requestWechatQr() async {
    setState(() => _requestingWechatQr = true);
    try {
      await _saveWechat(silent: true);
      final result = await ImChannelService.requestWechatQr();
      if (!mounted || result == null) return;
      final stateMap = result['state'];
      if (stateMap is Map) {
        setState(() => _applyState(ImChannelState.fromMap(stateMap)));
      }
      final ok = result['ok'] == true;
      final content = result['qrContent']?.toString() ?? '';
      if (ok && content.isNotEmpty) {
        await _showWechatQrSheet(content);
      } else {
        showToast(
          context.trLegacy(result['error']?.toString() ?? '获取微信二维码失败'),
          type: ToastType.error,
        );
      }
    } on PlatformException catch (error) {
      if (!mounted) return;
      showToast(
        context.trLegacy(error.message ?? '获取微信二维码失败'),
        type: ToastType.error,
      );
    } finally {
      if (mounted) setState(() => _requestingWechatQr = false);
    }
  }

  bool _telegramControllersMatch(TelegramImSettings settings) {
    return _telegramTokenController.text.trim() == settings.botToken.trim() &&
        _telegramApiBaseController.text.trim() == settings.apiBaseUrl.trim() &&
        _telegramAllowedController.text.trim() ==
            settings.allowedChatIds.trim() &&
        (int.tryParse(_telegramChunkController.text.trim()) ?? 3900) ==
            settings.chunkSize;
  }

  bool _wechatControllersMatch(WechatImSettings settings) {
    return _wechatTokenController.text.trim() == settings.token.trim() &&
        _wechatBaseUrlController.text.trim() == settings.baseUrl.trim() &&
        _wechatBotTypeController.text.trim() == settings.botType.trim() &&
        _wechatVersionController.text.trim() == settings.version.trim() &&
        (int.tryParse(_wechatChunkController.text.trim()) ?? 3000) ==
            settings.chunkSize;
  }

  Future<void> _clearSessions() async {
    try {
      final state = await ImChannelService.clearPeerSessions();
      if (!mounted || state == null) return;
      setState(() => _applyState(state));
      showToast(context.trLegacy('IM 会话已清除'), type: ToastType.success);
    } on PlatformException catch (error) {
      showToast(
        context.trLegacy(error.message ?? '清除失败'),
        type: ToastType.error,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    final state = _state;
    return Scaffold(
      backgroundColor: palette.pageBackground,
      appBar: CommonAppBar(title: 'IMessage', primary: true),
      body: SafeArea(
        child: _loading
            ? Center(
                child: CircularProgressIndicator(color: palette.accentPrimary),
              )
            : ListView(
                padding: const EdgeInsets.fromLTRB(18, 10, 18, 28),
                children: [
                  SettingsSectionTitle(
                    label: context.trLegacy('连接状态'),
                    bottomPadding: 10,
                  ),
                  _buildRuntimeStrip(state),
                  const SizedBox(height: 24),
                  _buildTelegramCard(state),
                  const SizedBox(height: 24),
                  _buildWechatCard(state),
                  const SizedBox(height: 24),
                  _buildSessionCard(state),
                ],
              ),
      ),
    );
  }

  Widget _buildRuntimeStrip(ImChannelState? state) {
    final palette = context.omniPalette;
    return _FlatInfoRow(
      icon: state?.running == true
          ? Icons.link_rounded
          : Icons.link_off_rounded,
      iconColor: state?.running == true
          ? palette.accentPrimary
          : palette.textTertiary,
      title: state?.running == true
          ? context.trLegacy('IM 渠道运行中')
          : context.trLegacy('IM 渠道未运行'),
      subtitle: context.trLegacy(
        '会话 ${state?.sessionCount ?? 0} · 任务 ${state?.pendingRunCount ?? 0}',
      ),
    );
  }

  Widget _buildTelegramCard(ImChannelState? state) {
    final telegram = state?.telegram ?? TelegramImSettings.fromMap(null);
    final status = state?.connector('telegram');
    return _ChannelCard(
      iconAsset: 'assets/home/imessage_telegram.svg',
      title: 'Telegram',
      subtitle: _statusText(status),
      trailing: _buildChannelSwitch(
        value: telegram.enabled,
        enabled: !_savingTelegram,
        onToggle: (value) => _saveTelegram(enabled: value),
      ),
      children: [
        _buildTextField(
          controller: _telegramTokenController,
          label: context.trLegacy('Bot Token'),
          obscureText: !_telegramTokenVisible,
          suffixIcon: IconButton(
            tooltip: _telegramTokenVisible
                ? context.trLegacy('隐藏')
                : context.trLegacy('显示'),
            onPressed: () {
              setState(() => _telegramTokenVisible = !_telegramTokenVisible);
            },
            icon: Icon(
              _telegramTokenVisible
                  ? Icons.visibility_off_outlined
                  : Icons.visibility_outlined,
            ),
          ),
        ),
        const SizedBox(height: 10),
        _buildAdvancedToggle(
          expanded: _telegramAdvancedExpanded,
          onTap: () {
            setState(() {
              _telegramAdvancedExpanded = !_telegramAdvancedExpanded;
            });
          },
        ),
        if (_telegramAdvancedExpanded) ...[
          const SizedBox(height: 8),
          _buildTextField(
            controller: _telegramApiBaseController,
            label: context.trLegacy('API Base'),
            keyboardType: TextInputType.url,
          ),
          const SizedBox(height: 10),
          _buildTextField(
            controller: _telegramAllowedController,
            label: context.trLegacy('Allowed Chat IDs'),
            minLines: 2,
            maxLines: 4,
          ),
          const SizedBox(height: 10),
          _buildTextField(
            controller: _telegramChunkController,
            label: context.trLegacy('消息分段长度'),
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          ),
        ],
      ],
    );
  }

  Widget _buildWechatCard(ImChannelState? state) {
    final wechat = state?.wechat ?? WechatImSettings.fromMap(null);
    final status = state?.connector('wechat');
    final sdkMissing = status?.sdkAvailable == false;
    return _ChannelCard(
      iconAsset: 'assets/home/imessage_wechat.svg',
      title: context.trLegacy('微信'),
      subtitle: sdkMissing
          ? context.trLegacy('OpeniLink SDK 未打包')
          : _statusText(status),
      trailing: _buildChannelSwitch(
        value: wechat.enabled,
        enabled: !_savingWechat,
        onToggle: (value) => _saveWechat(enabled: value),
      ),
      children: [
        _buildActionRow(
          busy: _savingWechat || _requestingWechatQr,
          primaryLabel: context.trLegacy('扫码绑定'),
          primaryIcon: Icons.qr_code_rounded,
          onPrimary: _requestingWechatQr ? null : _requestWechatQr,
        ),
        const SizedBox(height: 10),
        _buildAdvancedToggle(
          expanded: _wechatAdvancedExpanded,
          onTap: () {
            setState(() {
              _wechatAdvancedExpanded = !_wechatAdvancedExpanded;
            });
          },
        ),
        if (_wechatAdvancedExpanded) ...[
          const SizedBox(height: 8),
          _buildTextField(
            controller: _wechatTokenController,
            label: 'OpeniLink Token',
            obscureText: !_wechatTokenVisible,
            suffixIcon: IconButton(
              tooltip: _wechatTokenVisible
                  ? context.trLegacy('隐藏')
                  : context.trLegacy('显示'),
              onPressed: () {
                setState(() => _wechatTokenVisible = !_wechatTokenVisible);
              },
              icon: Icon(
                _wechatTokenVisible
                    ? Icons.visibility_off_outlined
                    : Icons.visibility_outlined,
              ),
            ),
          ),
          const SizedBox(height: 10),
          _buildTextField(
            controller: _wechatBaseUrlController,
            label: context.trLegacy('Base URL'),
            keyboardType: TextInputType.url,
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _buildTextField(
                  controller: _wechatBotTypeController,
                  label: context.trLegacy('Bot Type'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _buildTextField(
                  controller: _wechatVersionController,
                  label: context.trLegacy('Version'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          _buildTextField(
            controller: _wechatChunkController,
            label: context.trLegacy('消息分段长度'),
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          ),
        ],
      ],
    );
  }

  Widget _buildAdvancedToggle({
    required bool expanded,
    required VoidCallback onTap,
  }) {
    final palette = context.omniPalette;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 6),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              expanded
                  ? Icons.keyboard_arrow_up_rounded
                  : Icons.keyboard_arrow_down_rounded,
              color: palette.textSecondary,
              size: 20,
            ),
            const SizedBox(width: 4),
            Text(
              context.trLegacy('高级设置'),
              style: TextStyle(
                color: palette.textSecondary,
                fontSize: 12,
                fontWeight: FontWeight.w500,
                height: 1.4,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildChannelSwitch({
    required bool value,
    required bool enabled,
    required ValueChanged<bool> onToggle,
  }) {
    final palette = context.omniPalette;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: enabled ? () => onToggle(!value) : null,
      child: Padding(
        padding: const EdgeInsets.only(left: 8),
        child: AbsorbPointer(
          child: Opacity(
            opacity: enabled ? 1 : 0.5,
            child: FlutterSwitch(
              width: 32,
              height: 18.67,
              toggleSize: 11.3,
              padding: 3,
              activeColor: palette.accentPrimary,
              inactiveColor: palette.borderStrong,
              borderRadius: 28.75,
              value: value,
              onToggle: onToggle,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildSessionCard(ImChannelState? state) {
    final palette = context.omniPalette;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SettingsSectionTitle(label: context.trLegacy('会话'), bottomPadding: 10),
        Row(
          children: [
            Expanded(
              child: _FlatInfoRow(
                icon: Icons.forum_outlined,
                iconColor: palette.accentPrimary,
                title: context.trLegacy(
                  'Peer Sessions · ${state?.sessionCount ?? 0}',
                ),
                subtitle: context.trLegacy('清除后会重置当前消息渠道会话上下文'),
              ),
            ),
            const SizedBox(width: 12),
            TextButton.icon(
              onPressed: _clearSessions,
              icon: const Icon(Icons.delete_outline, size: 18),
              label: Text(context.trLegacy('清除')),
              style: TextButton.styleFrom(
                foregroundColor: palette.textSecondary,
                visualDensity: VisualDensity.compact,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    bool obscureText = false,
    int minLines = 1,
    int maxLines = 1,
    TextInputType? keyboardType,
    List<TextInputFormatter>? inputFormatters,
    Widget? suffixIcon,
  }) {
    return TextField(
      controller: controller,
      obscureText: obscureText,
      minLines: minLines,
      maxLines: obscureText ? 1 : maxLines,
      keyboardType: keyboardType,
      inputFormatters: inputFormatters,
      style: context.omniInputTextStyle,
      decoration: InputDecoration(labelText: label, suffixIcon: suffixIcon),
    );
  }

  Widget _buildActionRow({
    required bool busy,
    required String primaryLabel,
    required IconData primaryIcon,
    required VoidCallback? onPrimary,
    String? secondaryLabel,
    IconData? secondaryIcon,
    VoidCallback? onSecondary,
  }) {
    final primaryButton = FilledButton.icon(
      onPressed: busy ? null : onPrimary,
      icon: busy
          ? const SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(primaryIcon, size: 18),
      label: Text(primaryLabel, maxLines: 1, overflow: TextOverflow.ellipsis),
    );
    if (secondaryLabel == null || secondaryIcon == null) {
      return SizedBox(width: double.infinity, child: primaryButton);
    }

    final secondaryButton = OutlinedButton.icon(
      onPressed: busy ? null : onSecondary,
      icon: Icon(secondaryIcon, size: 18),
      label: Text(secondaryLabel, maxLines: 1, overflow: TextOverflow.ellipsis),
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 330) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              primaryButton,
              const SizedBox(height: 10),
              secondaryButton,
            ],
          );
        }
        return Row(
          children: [
            Expanded(child: primaryButton),
            const SizedBox(width: 10),
            Expanded(child: secondaryButton),
          ],
        );
      },
    );
  }

  String _statusText(ImConnectorStatus? status) {
    if (status == null || !status.enabled) return context.trLegacy('未启用');
    if (status.lastError.isNotEmpty) return context.trLegacy(status.lastError);
    if (status.connected) {
      return status.accountLabel.isEmpty
          ? context.trLegacy('已连接')
          : context.trLegacy('已连接 · ${status.accountLabel}');
    }
    if (status.running) return context.trLegacy('连接中');
    return context.trLegacy('未连接');
  }

  Future<void> _showWechatQrSheet(String content) async {
    final palette = context.omniPalette;
    final payload = _qrPayload(content);
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      backgroundColor: palette.surfacePrimary,
      builder: (sheetContext) {
        final media = MediaQuery.of(sheetContext);
        final qrSize = (media.size.shortestSide - 72)
            .clamp(168.0, 232.0)
            .toDouble();
        return SafeArea(
          child: ConstrainedBox(
            constraints: BoxConstraints(maxHeight: media.size.height * 0.86),
            child: SingleChildScrollView(
              padding: EdgeInsets.fromLTRB(
                18,
                6,
                18,
                22 + media.viewInsets.bottom,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    context.trLegacy('微信扫码绑定'),
                    style: TextStyle(
                      color: palette.textPrimary,
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 14),
                  _buildQrPreview(content, size: qrSize),
                  const SizedBox(height: 12),
                  SelectableText(
                    payload,
                    maxLines: 3,
                    style: TextStyle(
                      color: palette.textSecondary,
                      fontSize: 12,
                    ),
                  ),
                  const SizedBox(height: 14),
                  FilledButton.icon(
                    onPressed: () {
                      Clipboard.setData(ClipboardData(text: content));
                      Navigator.of(sheetContext).pop();
                      showToast(context.trLegacy('已复制'));
                    },
                    icon: const Icon(Icons.copy_rounded, size: 18),
                    label: Text(
                      context.trLegacy('复制绑定内容'),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildQrPreview(String content, {required double size}) {
    final bytes = _decodeQrBytes(content);
    final palette = context.omniPalette;
    if (bytes != null) {
      return Center(
        child: Container(
          width: size,
          height: size,
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: palette.borderSubtle),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: Image.memory(
              bytes,
              fit: BoxFit.contain,
              errorBuilder: (_, __, ___) =>
                  _buildGeneratedQrImage(_qrPayload(content)),
            ),
          ),
        ),
      );
    }

    return _buildGeneratedQr(content, size: size);
  }

  Widget _buildGeneratedQr(String content, {required double size}) {
    final payload = _qrPayload(content);
    final palette = context.omniPalette;
    if (payload.isEmpty) {
      return Container(
        height: 120,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: palette.surfaceSecondary,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: palette.borderSubtle),
        ),
        child: Icon(
          Icons.qr_code_2_rounded,
          size: 56,
          color: palette.textTertiary,
        ),
      );
    }

    return Center(
      child: Container(
        width: size,
        height: size,
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: palette.borderSubtle),
        ),
        child: _buildGeneratedQrImage(payload),
      ),
    );
  }

  Widget _buildGeneratedQrImage(String payload) {
    if (payload.isEmpty) {
      return const Icon(
        Icons.qr_code_2_rounded,
        size: 56,
        color: Colors.black38,
      );
    }
    return QrImageView(
      data: payload,
      version: QrVersions.auto,
      errorCorrectionLevel: QrErrorCorrectLevel.M,
      padding: EdgeInsets.zero,
      backgroundColor: Colors.white,
      dataModuleStyle: const QrDataModuleStyle(
        dataModuleShape: QrDataModuleShape.square,
        color: Colors.black,
      ),
      eyeStyle: const QrEyeStyle(
        eyeShape: QrEyeShape.square,
        color: Colors.black,
      ),
    );
  }

  String _qrPayload(String content) {
    final value = content.trim();
    if (value.isEmpty) return value;

    final decoded = _tryDecodeJson(value);
    if (decoded is Map) {
      for (final key in const [
        'qrcode',
        'qrCode',
        'qrContent',
        'url',
        'link',
      ]) {
        final nestedValue = decoded[key]?.toString().trim();
        if (nestedValue != null && nestedValue.isNotEmpty) {
          return nestedValue;
        }
      }
    }

    final markdownLink = RegExp(r'^\[[^\]]+\]\(([^)]+)\)$').firstMatch(value);
    if (markdownLink != null) return markdownLink.group(1)!.trim();

    final htmlLink = RegExp(
      r'''href\s*=\s*["']([^"']+)["']''',
      caseSensitive: false,
    ).firstMatch(value);
    if (htmlLink != null) return htmlLink.group(1)!.trim();

    final embeddedUri = RegExp(
      r'((?:https?|weixin|wx|links)://\S+)',
      caseSensitive: false,
    ).firstMatch(value);
    if (embeddedUri != null && value.contains(RegExp(r'\s'))) {
      return embeddedUri.group(1)!.replaceFirst(RegExp(r'[),.;]+$'), '');
    }

    return value;
  }

  dynamic _tryDecodeJson(String value) {
    try {
      return jsonDecode(value);
    } catch (_) {
      return null;
    }
  }

  Uint8List? _decodeQrBytes(String content) {
    final value = content.trim();
    if (value.startsWith('data:image')) {
      final marker = value.indexOf('base64,');
      if (marker >= 0) {
        return _tryBase64Decode(value.substring(marker + 'base64,'.length));
      }
    }

    final decoded = _tryDecodeJson(value);
    if (decoded is Map) {
      for (final key in const [
        'qrcode_img_content',
        'qrCodeImgContent',
        'image',
        'qrImage',
      ]) {
        final nestedValue = decoded[key]?.toString();
        if (nestedValue == null || nestedValue.trim().isEmpty) continue;
        final bytes = _decodeQrBytes(nestedValue);
        if (bytes != null) return bytes;
      }
    }

    if (RegExp(r'^[A-Za-z0-9+/=\r\n]+$').hasMatch(value) &&
        value.length > 128) {
      return _tryBase64Decode(value);
    }
    return null;
  }

  Uint8List? _tryBase64Decode(String value) {
    try {
      return base64Decode(value);
    } catch (_) {
      return null;
    }
  }
}

class _FlatInfoRow extends StatelessWidget {
  const _FlatInfoRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.iconColor,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final Color iconColor;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 8, 2, 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          SizedBox(
            width: 18,
            height: 18,
            child: Icon(icon, color: iconColor, size: 18),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    color: palette.textPrimary,
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                    height: 1.5,
                    fontFamily: 'PingFang SC',
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: palette.textSecondary,
                    fontSize: 11,
                    height: 1.55,
                    fontFamily: 'PingFang SC',
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ChannelCard extends StatelessWidget {
  const _ChannelCard({
    required this.iconAsset,
    required this.title,
    required this.subtitle,
    required this.trailing,
    required this.children,
  });

  final String iconAsset;
  final String title;
  final String subtitle;
  final Widget trailing;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final palette = context.omniPalette;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SettingsSectionTitle(label: title, bottomPadding: 10),
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            SizedBox(
              width: 18,
              height: 18,
              child: SvgPicture.asset(iconAsset, width: 18, height: 18),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                subtitle,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: palette.textSecondary,
                  fontSize: 11,
                  height: 1.55,
                  fontFamily: 'PingFang SC',
                ),
              ),
            ),
            const SizedBox(width: 12),
            trailing,
          ],
        ),
        const SizedBox(height: 14),
        ...children,
      ],
    );
  }
}
