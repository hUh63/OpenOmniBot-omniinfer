import 'package:flutter/material.dart';

/// 不规则形状画笔
class IrregularShapePainter extends CustomPainter {
  final Color color;
  final double overlap;
  
  IrregularShapePainter({required this.color, this.overlap = 0.2});
  
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;
    
    final path = Path();
    
    // 绘制不规则形状
    path.moveTo(0, size.height * 0.3);
    path.quadraticBezierTo(
      size.width * 0.2, 0,
      size.width * 0.4, size.height * 0.1,
    );
    path.lineTo(size.width * 0.7, size.height * 0.1);
    path.quadraticBezierTo(
      size.width * 0.9, size.height * 0.1,
      size.width, size.height * 0.4,
    );
    path.lineTo(size.width, size.height * 0.7);
    path.quadraticBezierTo(
      size.width * 0.9, size.height * 0.9,
      size.width * 0.7, size.height * 0.9,
    );
    path.lineTo(size.width * 0.3, size.height * 0.9);
    path.quadraticBezierTo(
      size.width * 0.1, size.height * 0.9,
      0, size.height * 0.6,
    );
    path.close();
    
    canvas.drawPath(path, paint);
  }
  
  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// 标签裁剪器
class TagClipper extends CustomClipper<Path> {
  @override
  Path getClip(Size size) {
    final path = Path();
    
    // 创建标签形状的路径
    path.moveTo(0, size.height * 0.3);
    
    // 左上角圆角
    path.quadraticBezierTo(
      0, 0,
      size.width * 0.15, 0,
    );
    
    // 右上角
    path.lineTo(size.width * 0.85, 0);
    path.quadraticBezierTo(
      size.width, 0,
      size.width, size.height * 0.3,
    );
    
    // 右下角
    path.lineTo(size.width, size.height * 0.7);
    path.quadraticBezierTo(
      size.width, size.height,
      size.width * 0.85, size.height,
    );
    
    // 左下角
    path.lineTo(size.width * 0.15, size.height);
    path.quadraticBezierTo(
      0, size.height,
      0, size.height * 0.7,
    );
    
    path.close();
    return path;
  }
  
  @override
  bool shouldReclip(covariant CustomClipper<Path> oldClipper) => false;
}
