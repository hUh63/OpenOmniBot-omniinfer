import 'package:flutter/material.dart';

/// 可滚动内容的底部渐变遮罩容器
/// 当内容可滚动且未触底时，在底部显示一个向上渐隐的遮罩；
/// 距离底部越远，遮罩越明显；越接近底部，遮罩越淡，触底时消失。
class ScrollFadeMask extends StatefulWidget {
  final Widget child;
  final EdgeInsetsGeometry? padding;
  final double gradientHeight;
  final Color backgroundColor;
  final double threshold;

  const ScrollFadeMask({
    super.key,
    required this.child,
    this.padding,
    this.gradientHeight = 56,
    this.backgroundColor = Colors.white,
    this.threshold = 120,
  });

  @override
  State<ScrollFadeMask> createState() => _ScrollFadeMaskState();
}

class _ScrollFadeMaskState extends State<ScrollFadeMask> {
  final ScrollController _controller = ScrollController();
  double _fadeOpacity = 0.0; // 0-1，0为不显示，1为最强
  final GlobalKey _contentKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    _controller.addListener(_updateFadeOpacity);
    // 首帧后计算一次
    WidgetsBinding.instance.addPostFrameCallback((_) => _updateFadeOpacity());
  }

  @override
  void dispose() {
    _controller.removeListener(_updateFadeOpacity);
    _controller.dispose();
    super.dispose();
  }

  void _updateFadeOpacity() {
    if (!_controller.hasClients) return;
    final position = _controller.position;
    final max = position.maxScrollExtent;
    // 不可滚动时隐藏遮罩
    if (max <= 0) {
      if (_fadeOpacity != 0) {
        setState(() => _fadeOpacity = 0);
      }
      return;
    }

    final pixels = position.pixels.clamp(0.0, max);
    final remaining = (max - pixels).clamp(0.0, max);
    
    // 增强初始渐变效果：大幅降低阈值，让即使少量内容超出边界也明显
    // 原本threshold=120，现在改为30，让效果更快显现
    final threshold = 30.0;
    final ratio = (remaining / threshold).clamp(0.0, 1.0);
    
    // 使用平方曲线让初始效果更强
    final target = ratio * ratio;
    
    // 确保初始状态有明显的渐变（至少40%不透明度）
    final minOpacity = remaining > 0 ? 0.4 : 0.0;
    final adjustedTarget = target.clamp(minOpacity, 1.0);

    if ((adjustedTarget - _fadeOpacity).abs() > 0.01) {
      setState(() => _fadeOpacity = adjustedTarget);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // 监听子内容尺寸变化（如流式文本增长），以便无需用户滚动也能更新遮罩强度
        NotificationListener<LayoutChangedNotification>(
          onNotification: (notification) {
            // 尺寸变化后下一帧更新一次
            WidgetsBinding.instance.addPostFrameCallback((_) => _updateFadeOpacity());
            return false;
          },
          child: SingleChildScrollView(
            controller: _controller,
            padding: widget.padding,
            child: SizeChangedLayoutNotifier(
              child: KeyedSubtree(
                key: _contentKey,
                child: widget.child,
              ),
            ),
          ),
        ),
        // 底部渐变遮罩
        Positioned(
          left: 0,
          right: 0,
          bottom: 0,
          child: IgnorePointer(
            child: Opacity(
              opacity: _fadeOpacity,
              child: Container(
                height: widget.gradientHeight,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                    colors: <Color>[
                      widget.backgroundColor,
                      widget.backgroundColor.withOpacity(0.0),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}


