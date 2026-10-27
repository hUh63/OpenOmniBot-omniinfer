import 'dart:async';

import 'package:flutter/services.dart';

typedef TitleCallback = void Function(String title);
typedef ContentCallback = void Function(String content);
typedef BeforeCloseChatBotCallback = void Function();

class ScreenDialogService {
  static const MethodChannel _channel = MethodChannel(
    'cn.com.omnimind.bot/ScreenDialogEvent',
  );

  /// 关闭/隐藏浮层都是按钮回调：原生不回包时会永久卡住。
  static const Duration _invokeTimeout = Duration(seconds: 10);

  static TitleCallback? _onTitleCallback;
  static ContentCallback? _onContentCallback;
  static BeforeCloseChatBotCallback? _onBeforeCloseChatBotDialog;

  static void initialize() {
    _channel.setMethodCallHandler(_handleMethod);
  }

  static Future<void> _handleMethod(MethodCall call) async {
    try {
      switch (call.method) {
        case 'onTitle':
          final String title = call.arguments ?? '';
          print(title);
          _onTitleCallback?.call(title);
          break;
        case 'onContent':
          final String content = call.arguments ?? '';
          print(content);
          _onContentCallback?.call(content);
          break;
        default:
          print('Unhandled method: ${call.method}');
      }
    } catch (e) {
      print('Failed to handle method call: $e');
    }
  }

  static void setOnTitleCallback(TitleCallback callback) {
    _onTitleCallback = callback;
  }

  static void setOnContentCallback(ContentCallback callback) {
    _onContentCallback = callback;
  }

  static void setOnBeforeCloseChatBotDialog(
    BeforeCloseChatBotCallback? callback,
  ) {
    _onBeforeCloseChatBotDialog = callback;
  }

  static void clearCallbacks() {
    print('ScreenDialogService.clearCallbacks()');
    _onTitleCallback = null;
    _onContentCallback = null;
    _onBeforeCloseChatBotDialog = null;
  }

  static Future<bool> closeDialog() async {
    try {
      // 先等结果、再摘 handler：原来先摘后 await，一旦超时/失败，
      // 原生 onTitle/onContent 就永久收不到了（只能重进页面才恢复）。
      final result = await _channel
          .invokeMethod('closeDialog')
          .timeout(_invokeTimeout);
      _channel.setMethodCallHandler(null);
      return result == "Success";
    } on PlatformException catch (e) {
      print('Failed to close dialog: ${e.message}');
      return false;
    } catch (e) {
      print('Failed to close dialog: $e');
      return false;
    }
  }

  static Future<bool> closeChatBotDialog() async {
    try {
      if (_onBeforeCloseChatBotDialog != null) {
        _onBeforeCloseChatBotDialog!.call();
      }
      final result = await _channel
          .invokeMethod('closeChatBotDialog')
          .timeout(_invokeTimeout);
      // 同 closeDialog：成功之后才摘 handler。
      _channel.setMethodCallHandler(null);
      return result == "Success";
    } catch (e) {
      print('Failed to close chat bot dialog: $e');
      return false;
    }
  }

  static Future<bool> hideForExternalActivity() async {
    try {
      final result = await _channel
          .invokeMethod('hideForExternalActivity')
          .timeout(_invokeTimeout);
      return result == true;
    } catch (e) {
      print('Failed to hide dialog for external activity: $e');
      return false;
    }
  }

  static Future<bool> restoreAfterExternalActivity() async {
    try {
      final result = await _channel
          .invokeMethod('restoreAfterExternalActivity')
          .timeout(_invokeTimeout);
      return result == true;
    } catch (e) {
      print('Failed to restore dialog after external activity: $e');
      return false;
    }
  }
}
