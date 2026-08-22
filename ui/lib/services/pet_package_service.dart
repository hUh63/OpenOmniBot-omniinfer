import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:archive/archive.dart';

typedef PetAtlasInspector = Future<PetAtlasInfo> Function(Uint8List bytes);

class PetAtlasInfo {
  final int width;
  final int height;

  const PetAtlasInfo({required this.width, required this.height});
}

class PetPackageInstallResult {
  final String id;
  final String displayName;
  final String description;
  final int spriteVersionNumber;
  final int animationRowCount;
  final Directory packageDirectory;
  final File spritesheetFile;

  const PetPackageInstallResult({
    required this.id,
    required this.displayName,
    required this.description,
    required this.spriteVersionNumber,
    required this.animationRowCount,
    required this.packageDirectory,
    required this.spritesheetFile,
  });
}

class PetPackageException implements Exception {
  final String message;

  const PetPackageException(this.message);

  @override
  String toString() => message;
}

class PetPackageInstaller {
  static const int _maxArchiveBytes = 32 * 1024 * 1024;
  static const int _maxExtractedBytes = 64 * 1024 * 1024;
  static const int _maxEntries = 64;
  static final RegExp _petIdPattern = RegExp(r'^[a-z0-9]+(?:-[a-z0-9]+)*$');

  final PetAtlasInspector inspectAtlas;

  const PetPackageInstaller({this.inspectAtlas = inspectCodexPetAtlas});

  Future<PetPackageInstallResult> installArchiveFile({
    required File archiveFile,
    required String petsRootPath,
  }) async {
    if (!await archiveFile.exists()) {
      throw const PetPackageException('宠物压缩包不存在');
    }
    final archiveLength = await archiveFile.length();
    if (archiveLength <= 0 || archiveLength > _maxArchiveBytes) {
      throw const PetPackageException('宠物压缩包为空或超过 32 MB');
    }
    return installArchiveBytes(
      archiveBytes: await archiveFile.readAsBytes(),
      petsRootPath: petsRootPath,
    );
  }

  Future<PetPackageInstallResult> installArchiveBytes({
    required Uint8List archiveBytes,
    required String petsRootPath,
  }) async {
    if (archiveBytes.isEmpty || archiveBytes.length > _maxArchiveBytes) {
      throw const PetPackageException('宠物压缩包为空或超过 32 MB');
    }
    final rootPath = petsRootPath.trim();
    if (rootPath.isEmpty) {
      throw const PetPackageException('宠物目录尚未初始化');
    }

    final archive = _decodeArchive(archiveBytes);
    final files = _validatedFiles(archive);
    final manifestEntries = files.entries
        .where((entry) => _baseName(entry.key) == 'pet.json')
        .toList(growable: false);
    if (manifestEntries.length != 1) {
      throw const PetPackageException('压缩包必须且只能包含一个 pet.json');
    }

    final manifestEntry = manifestEntries.single;
    final manifestBytes = manifestEntry.value.readBytes();
    if (manifestBytes == null || manifestBytes.isEmpty) {
      throw const PetPackageException('pet.json 为空');
    }
    final manifest = _decodeManifest(manifestBytes);
    final petId = (manifest['id'] ?? '').toString().trim();
    if (!_petIdPattern.hasMatch(petId)) {
      throw const PetPackageException(
        'pet.json 的 id 必须是类似 claude-pixel 的小写短横线名称',
      );
    }

    final displayName = (manifest['displayName'] ?? manifest['name'] ?? petId)
        .toString()
        .trim();
    if (displayName.isEmpty) {
      throw const PetPackageException('pet.json 缺少 displayName');
    }
    final description = (manifest['description'] ?? '').toString().trim();
    final spritesheetPath = (manifest['spritesheetPath'] ?? '')
        .toString()
        .trim();
    if (!_isSafeRelativeFileName(spritesheetPath) ||
        !_isSupportedSpritesheetName(spritesheetPath)) {
      throw const PetPackageException(
        'pet.json 的 spritesheetPath 必须指向 WebP 或 PNG 图集',
      );
    }

    final manifestDirectory = _directoryName(manifestEntry.key);
    final spritesheetArchivePath = manifestDirectory.isEmpty
        ? spritesheetPath
        : '$manifestDirectory/$spritesheetPath';
    final spritesheetEntry = files[spritesheetArchivePath];
    if (spritesheetEntry == null) {
      throw PetPackageException('压缩包缺少 $spritesheetPath');
    }
    final spritesheetBytes = spritesheetEntry.readBytes();
    if (spritesheetBytes == null || spritesheetBytes.isEmpty) {
      throw const PetPackageException('宠物图集为空');
    }
    _validateImageHeader(spritesheetPath, spritesheetBytes);

    final atlasInfo = await inspectAtlas(spritesheetBytes);
    final spriteVersionNumber = _parseSpriteVersion(
      manifest['spriteVersionNumber'],
    );
    final animationRowCount = _validateAtlasContract(
      atlasInfo,
      spriteVersionNumber,
    );
    final normalizedVersion = animationRowCount == 11 ? 2 : 1;

    final petsRoot = Directory(rootPath);
    await petsRoot.create(recursive: true);
    final packageDirectory = Directory(
      '${petsRoot.path}${Platform.pathSeparator}$petId',
    );
    await packageDirectory.create(recursive: true);
    final spritesheetFile = File(
      '${packageDirectory.path}${Platform.pathSeparator}$spritesheetPath',
    );
    await spritesheetFile.writeAsBytes(spritesheetBytes, flush: true);

    manifest['id'] = petId;
    manifest['displayName'] = displayName;
    manifest['description'] = description;
    manifest['spritesheetPath'] = spritesheetPath;
    if (normalizedVersion == 2) {
      manifest['spriteVersionNumber'] = 2;
    } else if (manifest['spriteVersionNumber'] != null) {
      manifest['spriteVersionNumber'] = 1;
    }
    final manifestFile = File(
      '${packageDirectory.path}${Platform.pathSeparator}pet.json',
    );
    await manifestFile.writeAsString(
      const JsonEncoder.withIndent('  ').convert(manifest),
      flush: true,
    );

    return PetPackageInstallResult(
      id: petId,
      displayName: displayName,
      description: description,
      spriteVersionNumber: normalizedVersion,
      animationRowCount: animationRowCount,
      packageDirectory: packageDirectory,
      spritesheetFile: spritesheetFile,
    );
  }

  Archive _decodeArchive(Uint8List bytes) {
    try {
      return ZipDecoder().decodeBytes(bytes, verify: true);
    } catch (_) {
      throw const PetPackageException('无法读取宠物压缩包，请确认它是有效的 ZIP 文件');
    }
  }

  Map<String, ArchiveFile> _validatedFiles(Archive archive) {
    if (archive.isEmpty || archive.length > _maxEntries) {
      throw const PetPackageException('宠物压缩包为空或文件数量过多');
    }
    final files = <String, ArchiveFile>{};
    var extractedBytes = 0;
    for (final entry in archive) {
      if (entry.isSymbolicLink) {
        throw const PetPackageException('宠物压缩包不能包含符号链接');
      }
      final entryName = entry.isFile
          ? entry.name
          : entry.name.replaceFirst(RegExp(r'/+$'), '');
      final normalizedName = _normalizeArchiveEntryName(entryName);
      if (!entry.isFile) {
        continue;
      }
      extractedBytes += entry.size;
      if (extractedBytes > _maxExtractedBytes) {
        throw const PetPackageException('宠物压缩包解压后超过 64 MB');
      }
      files[normalizedName] = entry;
    }
    return files;
  }

  String _normalizeArchiveEntryName(String rawName) {
    final name = rawName.trim();
    if (name.isEmpty ||
        name.startsWith('/') ||
        name.contains(r'\') ||
        name.contains(':')) {
      throw const PetPackageException('宠物压缩包包含不安全的文件路径');
    }
    final segments = name.split('/');
    if (segments.any((segment) => segment == '..' || segment.isEmpty)) {
      throw const PetPackageException('宠物压缩包包含不安全的文件路径');
    }
    return segments.where((segment) => segment != '.').join('/');
  }

  Map<String, dynamic> _decodeManifest(Uint8List bytes) {
    try {
      final decoded = jsonDecode(utf8.decode(bytes));
      if (decoded is! Map) {
        throw const FormatException();
      }
      return decoded.map((key, value) => MapEntry(key.toString(), value));
    } catch (_) {
      throw const PetPackageException('pet.json 不是有效的 JSON 对象');
    }
  }

  int _parseSpriteVersion(Object? rawValue) {
    if (rawValue == null) return 1;
    final parsed = rawValue is num
        ? rawValue.toInt()
        : int.tryParse(rawValue.toString().trim());
    if (parsed != 1 && parsed != 2) {
      throw const PetPackageException('spriteVersionNumber 仅支持 1 或 2');
    }
    return parsed!;
  }

  int _validateAtlasContract(PetAtlasInfo info, int spriteVersionNumber) {
    if (info.width != 1536) {
      throw const PetPackageException('宠物图集宽度必须是 1536 像素');
    }
    if (info.height == 1872 && spriteVersionNumber == 1) {
      return 9;
    }
    if (info.height == 2288 && spriteVersionNumber == 2) {
      return 11;
    }
    if (info.height == 2288) {
      throw const PetPackageException(
        '11 行图集必须在 pet.json 中声明 spriteVersionNumber: 2',
      );
    }
    if (spriteVersionNumber == 2) {
      throw const PetPackageException('v2 宠物图集尺寸必须是 1536×2288');
    }
    throw const PetPackageException('v1 宠物图集尺寸必须是 1536×1872');
  }

  void _validateImageHeader(String name, Uint8List bytes) {
    final lower = name.toLowerCase();
    final isWebp =
        bytes.length >= 12 &&
        bytes[0] == 0x52 &&
        bytes[1] == 0x49 &&
        bytes[2] == 0x46 &&
        bytes[3] == 0x46 &&
        bytes[8] == 0x57 &&
        bytes[9] == 0x45 &&
        bytes[10] == 0x42 &&
        bytes[11] == 0x50;
    final isPng =
        bytes.length >= 8 &&
        bytes[0] == 0x89 &&
        bytes[1] == 0x50 &&
        bytes[2] == 0x4e &&
        bytes[3] == 0x47 &&
        bytes[4] == 0x0d &&
        bytes[5] == 0x0a &&
        bytes[6] == 0x1a &&
        bytes[7] == 0x0a;
    if ((lower.endsWith('.webp') && !isWebp) ||
        (lower.endsWith('.png') && !isPng)) {
      throw const PetPackageException('宠物图集格式与文件扩展名不匹配');
    }
  }

  bool _isSafeRelativeFileName(String value) {
    return value.isNotEmpty &&
        !value.startsWith('/') &&
        !value.contains('/') &&
        !value.contains(r'\') &&
        !value.contains(':') &&
        value != '.' &&
        value != '..';
  }

  bool _isSupportedSpritesheetName(String value) {
    final lower = value.toLowerCase();
    return lower.endsWith('.webp') || lower.endsWith('.png');
  }

  String _baseName(String path) {
    final segments = path.split('/');
    return segments.isEmpty ? path : segments.last.toLowerCase();
  }

  String _directoryName(String path) {
    final separatorIndex = path.lastIndexOf('/');
    return separatorIndex < 0 ? '' : path.substring(0, separatorIndex);
  }
}

Future<PetAtlasInfo> inspectCodexPetAtlas(Uint8List bytes) async {
  try {
    final codec = await ui.instantiateImageCodec(bytes);
    final frame = await codec.getNextFrame();
    final image = frame.image;
    final info = PetAtlasInfo(width: image.width, height: image.height);
    image.dispose();
    codec.dispose();
    return info;
  } catch (_) {
    throw const PetPackageException('无法解码宠物图集');
  }
}
