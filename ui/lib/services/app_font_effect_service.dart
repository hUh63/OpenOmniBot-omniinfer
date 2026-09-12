import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:ui/services/storage_service.dart';

class AppFontEffectService {
  AppFontEffectService._();

  static const String enhancedLatinFamily = 'OmniTiemposText';
  static const String enhancedCjkFamily = 'OmniNotoSerifCjkSc';
  static const String defaultUiFamily = 'PingFang SC';

  static const List<String> enhancedFontFallback = <String>[
    enhancedCjkFamily,
    defaultUiFamily,
    'Microsoft YaHei',
    'Noto Sans CJK SC',
    'Roboto',
    'Arial',
  ];

  static const List<_FontResource> _fontResources = <_FontResource>[
    _FontResource(
      family: enhancedLatinFamily,
      cacheFileName: 'tiempostext-regular.ttf',
      remoteUrl:
          'https://omni-dl.1775885.xyz/2026-06/tiempostext-regular-webfont-fvbKcKMl_1781106875435.ttf',
    ),
    _FontResource(
      family: enhancedLatinFamily,
      cacheFileName: 'tiempostext-semibold.ttf',
      remoteUrl:
          'https://omni-dl.1775885.xyz/2026-06/tiempostext-semibold-webfont-CKUQAWup_1781106877245.ttf',
    ),
    _FontResource(
      family: enhancedLatinFamily,
      cacheFileName: 'tiempostext-regularitalic.ttf',
      remoteUrl:
          'https://omni-dl.1775885.xyz/2026-06/tiempostext-regularitalic-webfont-DrlEWHEs_1781106876337.ttf',
    ),
    _FontResource(
      family: enhancedLatinFamily,
      cacheFileName: 'tiempostext-semibolditalic.ttf',
      remoteUrl:
          'https://omni-dl.1775885.xyz/2026-06/tiempostext-semibolditalic-webfont-CtU2xEIm_1781106879016.ttf',
    ),
    _FontResource(
      family: enhancedCjkFamily,
      cacheFileName: 'NotoSerifCJKsc-Regular.otf',
      remoteUrl:
          'https://omni-dl.1775885.xyz/2026-06/NotoSerifCJKsc-Regular_1781106866895.otf',
    ),
    _FontResource(
      family: enhancedCjkFamily,
      cacheFileName: 'NotoSerifCJKsc-Medium.otf',
      remoteUrl:
          'https://omni-dl.1775885.xyz/2026-06/NotoSerifCJKsc-Medium_1781156013680.otf',
    ),
    _FontResource(
      family: enhancedCjkFamily,
      cacheFileName: 'NotoSerifCJKsc-SemiBold.otf',
      remoteUrl:
          'https://omni-dl.1775885.xyz/2026-06/NotoSerifCJKsc-SemiBold_1781156030493.otf',
    ),
  ];

  static bool _loaded = false;
  static bool _active = false;
  static Future<void>? _loadingFuture;

  static bool get isLoaded => _loaded;
  static bool get isActive => _active && _loaded;
  static String get currentFontFamily =>
      isActive ? enhancedLatinFamily : defaultUiFamily;
  static List<String>? get currentFontFamilyFallback =>
      isActive ? enhancedFontFallback : null;

  static String fontFamilyFor({required bool enhancedFonts}) {
    return enhancedFonts && _loaded ? enhancedLatinFamily : defaultUiFamily;
  }

  static List<String>? fontFallbackFor({required bool enhancedFonts}) {
    return enhancedFonts && _loaded ? enhancedFontFallback : null;
  }

  static Future<bool> loadFromStoredPreference() async {
    final enabled = StorageService.isEnhancedFontEffectsEnabled();
    if (enabled) {
      try {
        await ensureLoaded();
      } catch (error) {
        debugPrint(
          'AppFontEffectService: startup font download failed: $error',
        );
        setActive(false);
        return false;
      }
    }
    setActive(enabled);
    return isActive;
  }

  static void setActive(bool active) {
    _active = active && _loaded;
  }

  static Future<void> ensureLoaded() {
    if (_loaded) {
      return Future<void>.value();
    }
    final existing = _loadingFuture;
    if (existing != null) {
      return existing;
    }
    final future = _loadFonts();
    _loadingFuture = future;
    return future;
  }

  static Future<void> _loadFonts() async {
    try {
      final loaders = <String, FontLoader>{};
      for (final resource in _fontResources) {
        final loader = loaders.putIfAbsent(
          resource.family,
          () => FontLoader(resource.family),
        );
        loader.addFont(_loadFontData(resource));
      }

      for (final loader in loaders.values) {
        await loader.load();
      }
      _loaded = true;
    } catch (error, stackTrace) {
      _loadingFuture = null;
      debugPrint('AppFontEffectService: failed to load fonts: $error');
      debugPrintStack(stackTrace: stackTrace);
      rethrow;
    }
  }

  static Future<ByteData> _loadFontData(_FontResource resource) async {
    final remoteUrl = resource.remoteUrl.trim();
    if (remoteUrl.isEmpty) {
      throw StateError('Missing remote font URL for ${resource.cacheFileName}');
    }

    final cachedFile = await _cachedRemoteFontFile(resource.cacheFileName);
    if (await cachedFile.exists() && await cachedFile.length() > 0) {
      final bytes = await cachedFile.readAsBytes();
      return ByteData.sublistView(bytes);
    }

    final uri = Uri.parse(remoteUrl);
    final response = await http.get(uri);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HttpException('HTTP ${response.statusCode}', uri: uri);
    }
    await cachedFile.writeAsBytes(response.bodyBytes, flush: true);
    return ByteData.sublistView(response.bodyBytes);
  }

  static Future<File> _cachedRemoteFontFile(String fileName) async {
    final dir = await getApplicationSupportDirectory();
    final fontDir = Directory('${dir.path}/fonts');
    if (!await fontDir.exists()) {
      await fontDir.create(recursive: true);
    }
    return File('${fontDir.path}/$fileName');
  }
}

@immutable
class _FontResource {
  const _FontResource({
    required this.family,
    required this.cacheFileName,
    required this.remoteUrl,
  });

  final String family;
  final String cacheFileName;
  final String remoteUrl;
}
