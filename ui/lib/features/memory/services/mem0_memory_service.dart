import 'dart:convert';

import 'package:ui/features/memory/models/mem0_memory_item.dart';
import 'package:ui/l10n/legacy_text_localizer.dart';
import 'package:ui/services/workspace_memory_service.dart';

class Mem0MemoryService {
  static Future<Mem0MemorySnapshot> getMemories({
    bool forceRefresh = false,
    int? limit,
  }) async {
    try {
      final content = await WorkspaceMemoryService.getLongMemory();
      final parsedItems = _parseItems(content);
      // The memory screen is a view over the persisted workspace file.  Do
      // not silently hide older entries; a caller that truly needs a page may
      // request one explicitly.
      final items = limit == null
          ? parsedItems
          : parsedItems.take(limit).toList();
      return Mem0MemorySnapshot(
        configured: true,
        items: items,
        relations: const [],
        fetchedAt: DateTime.now(),
        fromCache: false,
        isStale: false,
        infoMessage: items.isEmpty
            ? (LegacyTextLocalizer.isEnglish
                  ? 'Workspace long-term memory is empty'
                  : 'workspace 长期记忆为空')
            : null,
      );
    } catch (e) {
      return Mem0MemorySnapshot(
        configured: true,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
    }
  }

  static Future<void> createMemory({
    required String memory,
    List<String> categories = const [],
    Map<String, dynamic> metadata = const {},
  }) async {
    final trimmed = memory.trim();
    if (trimmed.isEmpty) {
      throw Exception(
        LegacyTextLocalizer.isEnglish
            ? 'Memory content cannot be empty'
            : '记忆内容不能为空',
      );
    }
    await _applyMutation(
      (content) {
        final lines = content.split('\n');
        lines.add('- $trimmed');
        return lines.join('\n');
      },
    );
  }

  /// 读-改-写窗口里如果 agent / 夜间 rollup 已经往 MEMORY.md 追加了内容，
  /// 直接整文件覆盖会把那些行静默抹掉（一次丢多条记忆、没有版本号也没有提示）。
  /// 这里写完前复核一次：内容变了就在新内容上重放本次修改，最多两次。
  static Future<void> _applyMutation(
    String? Function(String content) mutate,
  ) async {
    var content = await WorkspaceMemoryService.getLongMemory();
    for (var attempt = 0; attempt < 2; attempt += 1) {
      final updated = mutate(content);
      if (updated == null) {
        throw Exception(
          LegacyTextLocalizer.isEnglish ? 'Memory not found' : '未找到对应记忆',
        );
      }
      final current = await WorkspaceMemoryService.getLongMemory();
      if (current != content) {
        content = current;
        continue;
      }
      await WorkspaceMemoryService.saveLongMemory(updated);
      return;
    }
    throw Exception(
      LegacyTextLocalizer.isEnglish
          ? 'Memory is being updated by another task; try again'
          : '记忆正在被其他任务更新，请稍后重试',
    );
  }

  static Future<void> updateMemory({
    required String memoryId,
    required String memory,
    List<String> categories = const [],
    Map<String, dynamic> metadata = const {},
  }) async {
    final trimmed = memory.trim();
    if (trimmed.isEmpty) {
      throw Exception(
        LegacyTextLocalizer.isEnglish
            ? 'Memory content cannot be empty'
            : '记忆内容不能为空',
      );
    }
    await _applyMutation((content) => _replaceById(content, memoryId, trimmed));
  }

  static Future<void> deleteMemory({required String memoryId}) async {
    await _applyMutation((content) => _deleteById(content, memoryId));
  }

  static List<Mem0MemoryItem> _parseItems(String content) {
    final lines = content
        .split('\n')
        .map((line) => line.trim())
        .where((line) => line.startsWith('- '))
        .map((line) => line.substring(2).trim())
        .where((line) => line.isNotEmpty)
        .toList();
    return lines.asMap().entries.map((entry) {
      final memory = entry.value;
      final id = _memoryId(memory);
      return Mem0MemoryItem(
        id: id,
        memory: memory,
        score: null,
        // 持久化文件里只有「- 内容」行，没有时间戳。原来硬写 DateTime.now()，
        // 于是所有记忆的相对时间永远显示「刚刚」，还随每次刷新往后跳——用户没法
        // 判断哪条是旧偏好。留空让卡片显示占位文案。
        createdAt: null,
        updatedAt: null,
        topLevelCategories: const [],
        metadata: const {},
        userId: 'workspace',
        agentId: 'omnibot-workspace-memory',
      );
    }).toList();
  }

  /// id 只由内容决定。
  /// 原来把行号编进 id：删掉一条后其后的 index 全部左移，旧快照里的 id 再也
  /// 匹配不上，表现为「刚删了一条，再改/删另一条就报未找到对应记忆」。
  static String _memoryId(String memory) {
    final raw = base64Url.encode(utf8.encode(memory.trim()));
    return raw.replaceAll('=', '');
  }

  static String? _replaceById(
    String content,
    String memoryId,
    String nextMemory,
  ) {
    final lines = content.split('\n');
    final bulletIndexes = <int>[];
    final bulletValues = <String>[];
    for (var i = 0; i < lines.length; i++) {
      final trimmed = lines[i].trim();
      if (!trimmed.startsWith('- ')) continue;
      final memory = trimmed.substring(2).trim();
      if (memory.isEmpty) continue;
      bulletIndexes.add(i);
      bulletValues.add(memory);
    }
    for (var i = 0; i < bulletValues.length; i++) {
      final id = _memoryId(bulletValues[i]);
      if (id == memoryId) {
        lines[bulletIndexes[i]] = '- $nextMemory';
        return lines.join('\n');
      }
    }
    return null;
  }

  static String? _deleteById(String content, String memoryId) {
    final lines = content.split('\n');
    final bulletIndexes = <int>[];
    final bulletValues = <String>[];
    for (var i = 0; i < lines.length; i++) {
      final trimmed = lines[i].trim();
      if (!trimmed.startsWith('- ')) continue;
      final memory = trimmed.substring(2).trim();
      if (memory.isEmpty) continue;
      bulletIndexes.add(i);
      bulletValues.add(memory);
    }
    for (var i = 0; i < bulletValues.length; i++) {
      final id = _memoryId(bulletValues[i]);
      if (id == memoryId) {
        lines.removeAt(bulletIndexes[i]);
        return lines.join('\n');
      }
    }
    return null;
  }
}
