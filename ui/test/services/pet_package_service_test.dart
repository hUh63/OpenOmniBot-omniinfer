import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ui/services/pet_package_service.dart';

void main() {
  late Directory tempDirectory;

  setUp(() async {
    tempDirectory = await Directory.systemTemp.createTemp(
      'omnibot-pet-package-test-',
    );
  });

  tearDown(() async {
    if (await tempDirectory.exists()) {
      await tempDirectory.delete(recursive: true);
    }
  });

  test('installs a nested Codex v1 pet package', () async {
    final installer = PetPackageInstaller(
      inspectAtlas: (_) async => const PetAtlasInfo(width: 1536, height: 1872),
    );
    final result = await installer.installArchiveBytes(
      archiveBytes: _petArchive(
        manifestPath: 'claude-pixel/pet.json',
        spritesheetPath: 'claude-pixel/spritesheet.webp',
      ),
      petsRootPath: tempDirectory.path,
    );

    expect(result.id, 'claude-pixel');
    expect(result.displayName, 'Claude Pixel');
    expect(result.spriteVersionNumber, 1);
    expect(result.animationRowCount, 9);
    expect(await result.spritesheetFile.exists(), isTrue);
    final installedManifest =
        jsonDecode(
              await File(
                '${result.packageDirectory.path}${Platform.pathSeparator}pet.json',
              ).readAsString(),
            )
            as Map<String, dynamic>;
    expect(installedManifest['spritesheetPath'], 'spritesheet.webp');
  });

  test('rejects unsafe archive paths', () async {
    final archive = Archive()
      ..add(ArchiveFile.string('../pet.json', '{}'))
      ..add(ArchiveFile.bytes('spritesheet.webp', _fakeWebpBytes()));
    final installer = PetPackageInstaller(
      inspectAtlas: (_) async => const PetAtlasInfo(width: 1536, height: 1872),
    );

    expect(
      () => installer.installArchiveBytes(
        archiveBytes: ZipEncoder().encodeBytes(archive),
        petsRootPath: tempDirectory.path,
      ),
      throwsA(isA<PetPackageException>()),
    );
  });

  test('requires spriteVersionNumber 2 for an 11-row atlas', () async {
    final installer = PetPackageInstaller(
      inspectAtlas: (_) async => const PetAtlasInfo(width: 1536, height: 2288),
    );

    expect(
      () => installer.installArchiveBytes(
        archiveBytes: _petArchive(),
        petsRootPath: tempDirectory.path,
      ),
      throwsA(
        isA<PetPackageException>().having(
          (error) => error.message,
          'message',
          contains('spriteVersionNumber'),
        ),
      ),
    );
  });
}

Uint8List _petArchive({
  String manifestPath = 'pet.json',
  String spritesheetPath = 'spritesheet.webp',
}) {
  final archive = Archive()
    ..add(
      ArchiveFile.string(
        manifestPath,
        jsonEncode({
          'id': 'claude-pixel',
          'displayName': 'Claude Pixel',
          'description': 'Animated test pet.',
          'spritesheetPath': 'spritesheet.webp',
        }),
      ),
    )
    ..add(ArchiveFile.bytes(spritesheetPath, _fakeWebpBytes()));
  return ZipEncoder().encodeBytes(archive);
}

Uint8List _fakeWebpBytes() {
  return Uint8List.fromList([
    0x52,
    0x49,
    0x46,
    0x46,
    0x04,
    0x00,
    0x00,
    0x00,
    0x57,
    0x45,
    0x42,
    0x50,
  ]);
}
