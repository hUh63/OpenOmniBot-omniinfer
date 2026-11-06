import 'package:flutter/material.dart';
import 'dart:ui';

// 自定义带模糊的菜单项
class BlurredMenuItem<T> extends PopupMenuEntry<T> {
  final T value;
  final Widget child;

  const BlurredMenuItem({
    required this.value,
    required this.child,
  });

  @override
  double get height => kMinInteractiveDimension;

  @override
  bool represents(T? value) => value == this.value;

  @override
  State<BlurredMenuItem<T>> createState() => _BlurredMenuItemState<T>();
}

class _BlurredMenuItemState<T> extends State<BlurredMenuItem<T>> {
  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 5, sigmaY: 5),
        child: Container(
          width: 120,
          decoration: BoxDecoration(
            color: const Color(0xFFF9F9F9).withOpacity(0.9),
            borderRadius: BorderRadius.circular(12),
          ),
          child: InkWell(
            onTap: () => Navigator.of(context).pop(widget.value),
            child: widget.child,
          ),
        ),
      ),
    );
  }
}