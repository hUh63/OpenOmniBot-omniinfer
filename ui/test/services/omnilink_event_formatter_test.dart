import 'package:flutter_test/flutter_test.dart';
import 'package:ui/services/omnilink_event_formatter.dart';

void main() {
  test('formats a valid notification timestamp as local hour and minute', () {
    final timestamp = DateTime(2026, 8, 6, 9, 7).millisecondsSinceEpoch;

    expect(formatOmniLinkNotificationTime(timestamp), '09:07');
  });

  test('does not invent a time for malformed or out-of-range metadata', () {
    expect(formatOmniLinkNotificationTime(null), isEmpty);
    expect(formatOmniLinkNotificationTime('not-a-timestamp'), isEmpty);
    expect(formatOmniLinkNotificationTime(double.nan), isEmpty);
    expect(formatOmniLinkNotificationTime(10e30), isEmpty);
  });
}
