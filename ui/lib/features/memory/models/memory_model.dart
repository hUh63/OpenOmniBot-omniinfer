import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/widgets.dart';
import 'package:ui/features/memory/pages/memory_center/widgets/tag_section.dart';

enum MemoryCardType {
  favorite, // 收藏卡片
}

class MemoryCardModel {
  final int id;
  final String title;
  final String? description;
  final int createdAt;
  final int updatedAt;
  final String? imagePath;
  final List<AppTag> tags;
  final bool isFavorite;
  final String? appName;
  final String? packageName; // 来源应用包名
  final ImageProvider? appIcon; // 来源应用图标
  final String? appSvgPath; // 来源应用 SVG 图标

  MemoryCardModel({
    required this.id,
    required this.title,
    this.description,
    required this.createdAt,
    required this.updatedAt,
    this.imagePath,
    this.tags = const [],
    this.isFavorite = false,
    this.appName,
    this.packageName,
    this.appIcon,
    this.appSvgPath,
  });

  bool get hasImage => imagePath != null && imagePath!.isNotEmpty;

  MemoryCardModel copyWith({
    int? id,
    String? title,
    String? description,
    int? createdAt,
    int? updatedAt,
    String? imagePath,
    List<AppTag>? tags,
    bool? isFavorite,
    String? appName,
    String? packageName,
    ImageProvider? appIcon,
    String? appSvgPath,
  }) {
    return MemoryCardModel(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      imagePath: imagePath ?? this.imagePath,
      tags: tags ?? this.tags,
      isFavorite: isFavorite ?? this.isFavorite,
      appName: appName ?? this.appName,
      packageName: packageName ?? this.packageName,
      appIcon: appIcon ?? this.appIcon,
      appSvgPath: appSvgPath ?? this.appSvgPath,
    );
  }
}

  Uint8List getBytesFromBase64(String? base64String) {
    if (base64String == null) return Uint8List(0);
    try {
      // 移除base64中的换行符
      base64String = base64String.replaceAll('\n', '');
      base64String = base64String.replaceAll('\r', '');
      return base64Decode(base64String);
    } catch (e) {
      return Uint8List(0);
    }
  }
