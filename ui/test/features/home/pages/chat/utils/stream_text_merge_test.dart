import 'package:flutter_test/flutter_test.dart';
import 'package:ui/features/home/pages/chat/utils/stream_text_merge.dart';

void main() {
  group('mergeAgentTextSnapshot', () {
    test('ignores regressive prefix snapshots', () {
      expect(mergeAgentTextSnapshot('Hello, world!', 'Hello'), 'Hello, world!');
    });

    test('replaces divergent snapshots instead of concatenating', () {
      expect(mergeAgentTextSnapshot('第一版：草稿内容', '最终版：完整内容'), '最终版：完整内容');
    });

    test('keeps emoji and markdown snapshots intact', () {
      expect(mergeAgentTextSnapshot('前缀😀', '前缀😀 **完成**'), '前缀😀 **完成**');
    });
  });

  group('mergeLegacyStreamingText', () {
    test('supports delta chunks and cumulative snapshots', () {
      expect(mergeLegacyStreamingText('Hello', ', world'), 'Hello, world');
      expect(
        mergeLegacyStreamingText('Hello, world', 'Hello, world!'),
        'Hello, world!',
      );
    });

    test('deduplicates overlapping delta chunks', () {
      expect(mergeLegacyStreamingText('Hello, wor', 'world'), 'Hello, world');
    });

    test('keeps tiny overlaps as normal delta text', () {
      expect(mergeLegacyStreamingText('abc', 'cde'), 'abccde');
    });

    test('ignores shorter divergent restarted table snapshots', () {
      const current =
          '好的，以下是一个示例表格：\n\n'
          '| 序号 | 姓名 |\n'
          '| --- | --- |\n'
          '| 1 | 张三 |';
      const incoming =
          '好的，以下是一个示例表格：\n\n'
          '| 序号 | 姓名 |\n'
          '|:---';

      expect(mergeLegacyStreamingText(current, incoming), current);
    });
  });
}
