import 'package:flutter/services.dart';

class OmnibotPdfPageInfo {
  final int width;
  final int height;

  const OmnibotPdfPageInfo({required this.width, required this.height});

  double get aspectRatio {
    if (width <= 0 || height <= 0) {
      return 1 / 1.414;
    }
    return width / height;
  }

  factory OmnibotPdfPageInfo.fromMap(Map<dynamic, dynamic> map) {
    return OmnibotPdfPageInfo(
      width: (map['width'] as num?)?.toInt() ?? 1,
      height: (map['height'] as num?)?.toInt() ?? 1,
    );
  }
}

class OmnibotPdfDocumentInfo {
  final int pageCount;
  final List<OmnibotPdfPageInfo> pages;

  const OmnibotPdfDocumentInfo({required this.pageCount, required this.pages});

  factory OmnibotPdfDocumentInfo.fromMap(Map<dynamic, dynamic> map) {
    final pages = ((map['pages'] as List?) ?? const [])
        .whereType<Map>()
        .map(OmnibotPdfPageInfo.fromMap)
        .toList();
    return OmnibotPdfDocumentInfo(
      pageCount: (map['pageCount'] as num?)?.toInt() ?? pages.length,
      pages: pages,
    );
  }
}

class OmnibotPdfPreviewService {
  static const MethodChannel _channel = MethodChannel(
    'cn.com.omnimind.bot/pdf_preview',
  );

  static final Map<String, Future<OmnibotPdfDocumentInfo>> _infoCache =
      <String, Future<OmnibotPdfDocumentInfo>>{};

  static Future<OmnibotPdfDocumentInfo> getDocumentInfo(String path) {
    final key = path.trim();
    // putIfAbsent 缓存的是 Future 本身：失败的那次也会被永久留下，之后同一路径
    // 永远返回同一个已失败的 Future（重试永远失败，只能重启进程）。
    // 失败时把条目摘掉，让下一次调用重新请求。
    final cached = _infoCache[key];
    if (cached != null) {
      return cached;
    }
    final future = () async {
      final result = await _channel
          .invokeMethod<Map<dynamic, dynamic>>(
            'getPdfInfo',
            <String, dynamic>{'path': key},
          )
          .timeout(const Duration(seconds: 30));
      if (result == null) {
        throw StateError('PDF 信息为空');
      }
      return OmnibotPdfDocumentInfo.fromMap(result);
    }();
    _infoCache[key] = future;
    // 失败时摘掉缓存条目（这里不 rethrow，避免产生未捕获的异步错误；
    // 调用方拿到的还是同一个失败 Future）。
    future.then<void>(
      (_) {},
      onError: (Object error, StackTrace stackTrace) {
        if (identical(_infoCache[key], future)) {
          _infoCache.remove(key);
        }
      },
    );
    return future;
  }

  static Future<Uint8List> renderPage({
    required String path,
    required int pageIndex,
    required int targetWidthPx,
  }) async {
    final result = await _channel.invokeMethod<Uint8List>(
      'renderPdfPage',
      <String, dynamic>{
        'path': path.trim(),
        'pageIndex': pageIndex,
        'targetWidthPx': targetWidthPx,
      },
    );
    if (result == null || result.isEmpty) {
      throw StateError('PDF 页面渲染结果为空');
    }
    return result;
  }

  static void clearCache() {
    _infoCache.clear();
  }
}
