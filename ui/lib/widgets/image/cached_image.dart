import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';

/// 缓存时长: 30天
/// 最大缓存数量: 300
class AppCacheManager {
  static const key = 'appCachedImageData';
  
  static CacheManager instance = CacheManager(
    Config(
      key,
      stalePeriod: const Duration(days: 30),
      maxNrOfCacheObjects: 300,
    ),
  );
}

class CachedImage extends StatelessWidget {
  /// 图片URL
  final String imageUrl;
  
  /// 图片宽度
  final double? width;
  
  /// 图片高度
  final double? height;
  
  /// 图片填充模式
  final BoxFit? fit;
  
  /// 圆角
  final BorderRadius? borderRadius;
  
  /// 加载时的占位组件
  final Widget? placeholder;
  
  /// 加载失败时的组件
  final Widget? errorWidget;

  const CachedImage({
    super.key,
    required this.imageUrl,
    this.width,
    this.height,
    this.fit,
    this.borderRadius,
    this.placeholder,
    this.errorWidget,
  });

  @override
  Widget build(BuildContext context) {
    Widget image = CachedNetworkImage(
      imageUrl: imageUrl,
      width: width,
      height: height,
      fit: fit ?? BoxFit.cover,
      cacheManager: AppCacheManager.instance,
      placeholder: placeholder != null
          ? (context, url) => placeholder!
          : null,
      errorWidget: errorWidget != null
          ? (context, url, error) => errorWidget!
          : (context, url, error) => SizedBox(
              width: width,
              height: height,
            ),
    );

    if (borderRadius != null) {
      image = ClipRRect(
        borderRadius: borderRadius!,
        child: image,
      );
    }

    return image;
  }
}

/// 获取带缓存的网络图片 ImageProvider
/// 使用 AppCacheManager 进行缓存管理
CachedNetworkImageProvider getCachedNetworkImageProvider(String imageUrl) {
  return CachedNetworkImageProvider(
    imageUrl,
    cacheManager: AppCacheManager.instance,
  );
}

