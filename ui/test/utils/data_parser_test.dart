import 'package:flutter_test/flutter_test.dart';
import 'package:ui/utils/data_parser.dart';

void main() {
  test(
    'extractChatTaskText preserves whitespace and punctuation across chunks',
    () {
      const chunk = '''
[
  {"choices":[{"delta":{"content":"Hello"}}]},
  {"choices":[{"delta":{"content":","}}]},
  {"choices":[{"delta":{"content":" "}}]},
  {"choices":[{"delta":{"content":"world"}}]},
  {"choices":[{"delta":{"content":"!"}}]},
  {"choices":[{"delta":{"content":"\\n"}}]},
  {"choices":[{"delta":{"content":"  Next line"}}]},
  {"choices":[{"delta":{},"finish_reason":"stop"}]}
]
''';

      final result = extractChatTaskText(chunk, fallbackToRawText: false);

      expect(result, 'Hello, world!\n  Next line');
    },
  );

  test(
    'extractChatTaskThinking preserves whitespace and punctuation across chunks',
    () {
      const chunk = '''
[
  {"choices":[{"delta":{"reasoning_content":"先想"}}]},
  {"choices":[{"delta":{"reasoning_content":"："}}]},
  {"choices":[{"delta":{"reasoning_content":"\\n"}}]},
  {"choices":[{"delta":{"reasoning_content":"  再做"}}]},
  {"choices":[{"delta":{"reasoning_content":"。"}}]}
]
''';

      final result = extractChatTaskThinking(chunk, fallbackToRawText: false);

      expect(result, '先想：\n  再做。');
    },
  );
}
