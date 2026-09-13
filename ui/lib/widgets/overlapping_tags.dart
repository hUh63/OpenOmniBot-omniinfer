import 'package:flutter/material.dart';
import 'irregular_tag_painter.dart';

class TagData {
  final String text;
  final Color color;
  final IconData? icon;
  
  TagData({required this.text, required this.color, this.icon});
}

/// 重叠标签组件
class OverlappingTags extends StatelessWidget {
  final List<TagData> tags;
  final double overlapOffset;
  
  const OverlappingTags({
    Key? key,
    required this.tags,
    this.overlapOffset = 30.0,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    if (tags.isEmpty) return const SizedBox.shrink();
    
    return SizedBox(
      height: 50 + (tags.length - 1) * 15,
      child: Stack(
        children: tags.asMap().entries.map((entry) {
          int index = entry.key;
          TagData tag = entry.value;
          
          return Positioned(
            left: index * overlapOffset,
            top: index * 10.0,
            child: CustomPaint(
              painter: IrregularShapePainter(color: tag.color),
              child: Container(
                width: 100,
                height: 40,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (tag.icon != null) ...[
                      Icon(tag.icon, size: 14, color: Colors.white),
                      const SizedBox(width: 4),
                    ],
                    Flexible(
                      child: Text(
                        tag.text,
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}

/// 裁剪式重叠标签
class ClippedOverlappingTags extends StatelessWidget {
  final List<TagData> tags;
  final double overlapOffset;
  
  const ClippedOverlappingTags({
    Key? key,
    required this.tags,
    this.overlapOffset = 70.0,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    if (tags.isEmpty) return const SizedBox.shrink();
    
    return SizedBox(
      height: 50 + (tags.length - 1) * 20,
      child: Stack(
        children: tags.asMap().entries.map((entry) {
          int index = entry.key;
          TagData tag = entry.value;
          
          return Positioned(
            left: index * overlapOffset,
            top: index * 15.0,
            child: ClipPath(
              clipper: TagClipper(),
              child: Container(
                width: 120,
                height: 50,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      tag.color,
                      tag.color.withOpacity(0.8),
                    ],
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: tag.color.withOpacity(0.3),
                      blurRadius: 8,
                      offset: const Offset(2, 2),
                    ),
                  ],
                ),
                child: Center(
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (tag.icon != null) ...[
                        Icon(tag.icon, size: 16, color: Colors.white),
                        const SizedBox(width: 6),
                      ],
                      Text(
                        tag.text,
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}
