import 'package:flutter/material.dart';

/// 思考中浮动三个点动画组件
/// 用于替代"小万正在思考..."文字，展示更简洁的加载状态
class ThinkingDotsIndicator extends StatefulWidget {
  /// 点的颜色
  final Color dotColor;
  
  /// 点的大小
  final double dotSize;
  
  /// 点之间的间距
  final double spacing;

  const ThinkingDotsIndicator({
    super.key,
    this.dotColor = const Color(0xFF999999),
    this.dotSize = 8.0,
    this.spacing = 4.0,
  });

  @override
  State<ThinkingDotsIndicator> createState() => _ThinkingDotsIndicatorState();
}

class _ThinkingDotsIndicatorState extends State<ThinkingDotsIndicator>
    with TickerProviderStateMixin {
  late List<AnimationController> _controllers;
  late List<Animation<double>> _animations;

  @override
  void initState() {
    super.initState();
    _initAnimations();
  }

  void _initAnimations() {
    _controllers = List.generate(
      3,
      (index) => AnimationController(
        vsync: this,
        duration: const Duration(milliseconds: 600),
      ),
    );

    _animations = _controllers.map((controller) {
      return Tween<double>(begin: 0.0, end: 1.0).animate(
        CurvedAnimation(
          parent: controller,
          curve: Curves.easeInOut,
        ),
      );
    }).toList();

    // 依次启动动画，形成波浪效果
    for (int i = 0; i < _controllers.length; i++) {
      Future.delayed(Duration(milliseconds: i * 150), () {
        if (mounted) {
          _controllers[i].repeat(reverse: true);
        }
      });
    }
  }

  @override
  void dispose() {
    for (var controller in _controllers) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(3, (index) {
        return AnimatedBuilder(
          animation: _animations[index],
          builder: (context, child) {
            return Container(
              margin: EdgeInsets.symmetric(horizontal: widget.spacing / 2),
              child: Transform.translate(
                offset: Offset(0, -4 * _animations[index].value),
                child: Container(
                  width: widget.dotSize,
                  height: widget.dotSize,
                  decoration: BoxDecoration(
                    color: widget.dotColor.withValues(
                      alpha: 0.4 + 0.6 * _animations[index].value,
                    ),
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            );
          },
        );
      }),
    );
  }
}

