import 'dart:math' as math;

const int _kMinimumStreamingOverlapLength = 3;

bool shouldIgnoreRegressiveStreamingSnapshot(String current, String incoming) {
  if (current.isEmpty || incoming.isEmpty) {
    return false;
  }
  return incoming.length < current.length && current.startsWith(incoming);
}

String mergeAgentTextSnapshot(String current, String incoming) {
  if (incoming.isEmpty) return current;
  if (current.isEmpty) return incoming;
  if (incoming == current) return current;
  if (shouldIgnoreRegressiveStreamingSnapshot(current, incoming)) {
    return current;
  }
  return incoming;
}

String mergeLegacyStreamingText(String current, String incoming) {
  if (incoming.isEmpty) return current;
  if (current.isEmpty) return incoming;
  if (incoming == current) return current;
  if (shouldIgnoreRegressiveStreamingSnapshot(current, incoming)) {
    return current;
  }
  if (incoming.length >= current.length && incoming.startsWith(current)) {
    return incoming;
  }
  final overlap = _longestSuffixPrefixOverlap(current, incoming);
  if (overlap >= _kMinimumStreamingOverlapLength) {
    return current + incoming.substring(overlap);
  }
  final commonPrefixLength = _commonPrefixLength(current, incoming);
  if (_looksLikeDivergentStreamingSnapshot(
    current,
    incoming,
    commonPrefixLength,
  )) {
    return incoming.length >= current.length ? incoming : current;
  }
  return current + incoming;
}

int _longestSuffixPrefixOverlap(String current, String incoming) {
  final maxOverlap = math.min(math.min(current.length, incoming.length), 4096);
  for (var length = maxOverlap; length > 0; length -= 1) {
    if (incoming.startsWith(current.substring(current.length - length))) {
      return length;
    }
  }
  return 0;
}

int _commonPrefixLength(String current, String incoming) {
  final maxLength = math.min(current.length, incoming.length);
  var index = 0;
  while (index < maxLength &&
      current.codeUnitAt(index) == incoming.codeUnitAt(index)) {
    index += 1;
  }
  return index;
}

bool _looksLikeDivergentStreamingSnapshot(
  String current,
  String incoming,
  int commonPrefixLength,
) {
  if (commonPrefixLength < 12) {
    return false;
  }
  final shorterLength = math.min(current.length, incoming.length);
  if (shorterLength == 0) {
    return false;
  }
  return commonPrefixLength >= 24 || commonPrefixLength / shorterLength >= 0.6;
}
