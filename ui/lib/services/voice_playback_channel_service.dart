import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// 朗读/暂停/重播都是消息卡片上的按钮：原生不回包时 await 会挂死，
/// 卡片语音状态就永久停在「合成中」。
const Duration _invokeTimeout = Duration(seconds: 30);

const MethodChannel _voicePlaybackChannel = MethodChannel(
  'cn.com.omnimind.bot/VoicePlayback',
);
const EventChannel _voicePlaybackEvents = EventChannel(
  'cn.com.omnimind.bot/VoicePlaybackEvents',
);

enum VoicePlaybackStatus {
  idle,
  synthesizing,
  playing,
  paused,
  completed,
  error;

  static VoicePlaybackStatus fromWire(String? value) {
    final normalized = value?.trim().toLowerCase() ?? '';
    return VoicePlaybackStatus.values.firstWhere(
      (status) => status.name == normalized,
      orElse: () => VoicePlaybackStatus.idle,
    );
  }
}

class VoicePlaybackEvent {
  final String messageId;
  final VoicePlaybackStatus status;
  final String error;
  final bool canReplay;

  const VoicePlaybackEvent({
    required this.messageId,
    required this.status,
    this.error = '',
    this.canReplay = false,
  });

  factory VoicePlaybackEvent.fromMap(Map<dynamic, dynamic>? map) {
    return VoicePlaybackEvent(
      messageId: (map?['messageId'] ?? '').toString(),
      status: VoicePlaybackStatus.fromWire(map?['status']?.toString()),
      error: (map?['error'] ?? '').toString(),
      canReplay: map?['canReplay'] == true,
    );
  }
}

class VoicePlaybackChannelService {
  // 原生 EventChannel 只有一个 message handler：每次 receiveBroadcastStream() 都会
  // 按 channel 名覆盖它，并把上一个订阅者变成孤儿。而把 map(...).asBroadcastStream()
  // 的结果缓存成 static 字段更糟 —— 源一旦 error/done，这条 broadcast 流就永久关闭，
  // 之后任何 listen 都会立刻 onDone，语音状态再也收不到原生事件（卡片永远停在
  // 「合成中/播放中」，只能杀进程恢复）。
  // 所以自己维护一个广播 controller：只订阅一次原生通道，订阅失败或结束时复位标记，
  // 让下一次访问重新连上；消费者 cancel 自己的 listener 也不会拆掉原生订阅。
  static final StreamController<VoicePlaybackEvent> _controller =
      StreamController<VoicePlaybackEvent>.broadcast();
  static StreamSubscription<dynamic>? _nativeSubscription;

  static Stream<VoicePlaybackEvent> get events {
    _ensureNativeSubscription();
    return _controller.stream;
  }

  static void _ensureNativeSubscription() {
    if (_nativeSubscription != null) return;
    _nativeSubscription = _voicePlaybackEvents.receiveBroadcastStream().listen(
      (event) {
        _controller.add(
          VoicePlaybackEvent.fromMap(
            event is Map ? event : const <String, dynamic>{},
          ),
        );
      },
      onError: (Object error, StackTrace stack) {
        _teardownNativeSubscription();
        _controller.addError(error, stack);
      },
      onDone: _teardownNativeSubscription,
      cancelOnError: true,
    );
  }

  static void _teardownNativeSubscription() {
    _nativeSubscription?.cancel();
    _nativeSubscription = null;
  }

  static Future<bool> speakText({
    required String messageId,
    required String text,
    bool enqueue = false,
    bool preferStreaming = true,
  }) async {
    try {
      final result = await _voicePlaybackChannel
          .invokeMethod<bool>('speakText', {
            'messageId': messageId,
            'text': text,
            'enqueue': enqueue,
            'preferStreaming': preferStreaming,
          })
          .timeout(_invokeTimeout);
      return result == true;
    } on PlatformException {
      return false;
    } catch (error) {
      debugPrint('[VoicePlayback] request failed: $error');
      return false;
    }
  }

  static Future<bool> replayText({
    required String messageId,
    required String text,
  }) async {
    try {
      final result = await _voicePlaybackChannel
          .invokeMethod<bool>(
            'replayText',
            {'messageId': messageId, 'text': text},
          )
          .timeout(_invokeTimeout);
      return result == true;
    } on PlatformException {
      return false;
    } catch (error) {
      debugPrint('[VoicePlayback] request failed: $error');
      return false;
    }
  }

  static Future<bool> pausePlayback(String messageId) async {
    try {
      final result = await _voicePlaybackChannel
          .invokeMethod<bool>(
            'pausePlayback',
            {'messageId': messageId},
          )
          .timeout(_invokeTimeout);
      return result == true;
    } on PlatformException {
      return false;
    } catch (error) {
      debugPrint('[VoicePlayback] request failed: $error');
      return false;
    }
  }

  static Future<bool> resumePlayback(String messageId) async {
    try {
      final result = await _voicePlaybackChannel
          .invokeMethod<bool>(
            'resumePlayback',
            {'messageId': messageId},
          )
          .timeout(_invokeTimeout);
      return result == true;
    } on PlatformException {
      return false;
    } catch (error) {
      debugPrint('[VoicePlayback] request failed: $error');
      return false;
    }
  }

  static Future<bool> stopPlayback(String messageId) async {
    try {
      final result = await _voicePlaybackChannel
          .invokeMethod<bool>(
            'stopPlayback',
            {'messageId': messageId},
          )
          .timeout(_invokeTimeout);
      return result == true;
    } on PlatformException {
      return false;
    } catch (error) {
      debugPrint('[VoicePlayback] request failed: $error');
      return false;
    }
  }
}
