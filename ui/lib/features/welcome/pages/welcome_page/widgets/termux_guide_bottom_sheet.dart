import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ui/theme/app_colors.dart';
import 'package:ui/utils/ui.dart';
import 'package:ui/widgets/bottom_sheet_bg.dart';
import 'package:ui/widgets/gradient_button.dart';

class TermuxGuideBottomSheet extends StatefulWidget {
  final Future<bool> Function() checkTermuxInstalled;
  final Future<bool> Function() openTermuxApp;
  final Future<void> Function() onCompleted;

  const TermuxGuideBottomSheet({
    super.key,
    required this.checkTermuxInstalled,
    required this.openTermuxApp,
    required this.onCompleted,
  });

  static Future<bool?> show(
    BuildContext context, {
    required Future<bool> Function() checkTermuxInstalled,
    required Future<bool> Function() openTermuxApp,
    required Future<void> Function() onCompleted,
  }) {
    return showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => TermuxGuideBottomSheet(
        checkTermuxInstalled: checkTermuxInstalled,
        openTermuxApp: openTermuxApp,
        onCompleted: onCompleted,
      ),
    );
  }

  @override
  State<TermuxGuideBottomSheet> createState() => _TermuxGuideBottomSheetState();
}

class _TermuxGuideBottomSheetState extends State<TermuxGuideBottomSheet>
    with WidgetsBindingObserver {
  bool _isInstalled = false;
  bool _isLoading = true;
  bool _isOpening = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshInstallState();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshInstallState();
    }
  }

  Future<void> _refreshInstallState() async {
    final installed = await widget.checkTermuxInstalled();
    if (!mounted) {
      return;
    }
    setState(() {
      _isInstalled = installed;
      _isLoading = false;
    });
  }

  Future<void> _handleOpenTermux() async {
    if (_isOpening) {
      return;
    }

    setState(() {
      _isOpening = true;
    });

    try {
      final opened = await widget.openTermuxApp();
      if (!opened) {
        showToast('当前设备暂不支持内嵌终端，仅支持 arm64-v8a', type: ToastType.warning);
      } else {
        showToast('内嵌终端环境已可用', type: ToastType.success);
      }
    } on PlatformException catch (e) {
      showToast(e.message ?? '检查内嵌终端失败', type: ToastType.error);
    } catch (e) {
      showToast('检查内嵌终端失败', type: ToastType.error);
    } finally {
      if (mounted) {
        setState(() {
          _isOpening = false;
        });
      }
    }
  }

  Future<void> _handleCompleted() async {
    if (_isLoading) {
      return;
    }
    if (!_isInstalled) {
      showToast('当前设备暂不支持内嵌终端，仅支持 arm64-v8a', type: ToastType.warning);
      return;
    }

    await widget.onCompleted();
    if (!mounted) {
      return;
    }
    Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    return BottomSheetBg(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildHeader(),
            const SizedBox(height: 16),
            _buildHeroCard(),
            const SizedBox(height: 16),
            _buildGuideList(),
            const SizedBox(height: 16),
            _buildCommandHint(),
            const SizedBox(height: 18),
            _buildActionRow(),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Row(
      children: [
        const Expanded(
          child: Text(
            '内嵌终端能力',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
              color: AppColors.text,
            ),
          ),
        ),
        GestureDetector(
          onTap: () => Navigator.of(context).pop(),
          child: Text(
            '稍后设置',
            style: TextStyle(
              color: AppColors.text.withValues(alpha: 0.55),
              fontSize: 12,
              fontWeight: FontWeight.w400,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildHeroCard() {
    final statusText = _isLoading
        ? '正在检测内嵌终端'
        : _isInstalled
        ? '已检测到内嵌终端能力'
        : '当前设备暂不支持内嵌终端';
    final statusColor = _isInstalled
        ? const Color(0xFF1E9E63)
        : const Color(0xFFEF6B5F);
    final statusBackground = _isInstalled
        ? const Color(0x141E9E63)
        : const Color(0x14EF6B5F);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.94),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0x14000000)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: statusBackground,
              borderRadius: BorderRadius.circular(999),
            ),
            child: Text(
              statusText,
              style: TextStyle(
                color: statusColor,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          const SizedBox(height: 12),
          const Text(
            '开启后，小万会直接在应用内所选的 Alpine 或 Ubuntu（proot）环境执行终端命令，不需要再单独安装 Termux；/workspace 会映射到 Omnibot 应用内部工作区。',
            style: TextStyle(
              color: AppColors.text,
              fontSize: 14,
              fontWeight: FontWeight.w500,
              height: 1.6,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '这是一个可选增强能力，不影响基础 Agent 功能。',
            style: TextStyle(
              color: AppColors.text.withValues(alpha: 0.65),
              fontSize: 12,
              fontWeight: FontWeight.w400,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGuideList() {
    return Column(
      children: const [
        _GuideItem(
          index: '1',
          title: '无需独立 App',
          description: '终端能力已经集成在应用内部，不需要再下载、安装或打开额外的终端应用。',
        ),
        SizedBox(height: 12),
        _GuideItem(
          index: '2',
          title: '首次会自动初始化',
          description:
              '首次使用时，应用会在后台解压所选的 Alpine 或 Ubuntu rootfs，并按需准备 git、python、node 等基础 CLI 依赖。',
        ),
        SizedBox(height: 12),
        _GuideItem(
          index: '3',
          title: '工作区仍叫 /workspace',
          description:
              '所选终端系统内依然使用 /workspace 这套路径语义，但底层已经切到 Omnibot 应用内部目录，更适合运行 uv、venv 和其它开发工具。',
        ),
        SizedBox(height: 12),
        _GuideItem(
          index: '4',
          title: '异常时重新检查',
          description: '如果终端输出异常或基础包缺失，可以在设置页重新执行环境检查，并确认网络正常。',
        ),
      ],
    );
  }

  Widget _buildCommandHint() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFF0F172A),
        borderRadius: BorderRadius.circular(14),
      ),
      child: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '可用这些命令快速验证环境：',
            style: TextStyle(
              color: Color(0xFFDDE7FF),
              fontSize: 12,
              fontWeight: FontWeight.w500,
            ),
          ),
          SizedBox(height: 10),
          SelectableText(
            'pwd\n'
            'ls /workspace\n'
            'git --version\n'
            'python3 --version\n'
            'node --version',
            style: TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontFamily: 'monospace',
              height: 1.55,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionRow() {
    return Row(
      children: [
        Expanded(
          child: GestureDetector(
            onTap: _handleOpenTermux,
            child: Container(
              height: 44,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppColors.buttonPrimary),
              ),
              alignment: Alignment.center,
              child: Text(
                _isOpening ? '检查中...' : '检查环境',
                style: const TextStyle(
                  color: AppColors.buttonPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: GradientButton(
            text: '我已开启',
            width: double.infinity,
            height: 44,
            borderRadius: 10,
            enabled: !_isLoading && _isInstalled,
            onTap: _handleCompleted,
            textStyle: const TextStyle(
              color: Colors.white,
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ],
    );
  }
}

class _GuideItem extends StatelessWidget {
  final String index;
  final String title;
  final String description;

  const _GuideItem({
    required this.index,
    required this.title,
    required this.description,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 24,
          height: 24,
          decoration: const BoxDecoration(
            color: Color(0xFFECF5FF),
            shape: BoxShape.circle,
          ),
          alignment: Alignment.center,
          child: Text(
            index,
            style: const TextStyle(
              color: AppColors.buttonPrimary,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  color: AppColors.text,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                description,
                style: TextStyle(
                  color: AppColors.text.withValues(alpha: 0.68),
                  fontSize: 12,
                  fontWeight: FontWeight.w400,
                  height: 1.55,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
