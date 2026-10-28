import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Overlay服务，用于与原生OverlayChannel通信
/// !!暂不使用!!
class OverlayService {
  static const MethodChannel _channel = MethodChannel(
    'cn.com.omnimind.bot/overlay',
  );

  /// 悬浮层操作都是点击/生命周期回调：原生不回包时会永久挂起。
  static const Duration _invokeTimeout = Duration(seconds: 10);

  /// 显示消息提示（在MessageView中显示）
  /// [message] 要显示的消息内容
  static Future<bool> showMessage(String message) async {
    try {
      final result = await _channel.invokeMethod('showMessage', {
        'message': message,
      }).timeout(_invokeTimeout);
      return result == true;
    } catch (e) {
      if (kDebugMode) {
        print('显示消息失败: $e');
      }
      return false;
    }
  }

  static Future<bool> setPetOverlayImagePath(
    String path, {
    String selectedId = '',
  }) async {
    try {
      final result = await _channel.invokeMethod<bool>(
        'setPetOverlayImagePath',
        {'path': path, 'selectedId': selectedId},
      ).timeout(_invokeTimeout);
      return result == true;
    } catch (e) {
      if (kDebugMode) {
        print('Failed to set pet overlay image: $e');
      }
      return false;
    }
  }

  static Future<bool> showPetOverlay() async {
    try {
      final result = await _channel.invokeMethod<bool>('showPetOverlay').timeout(_invokeTimeout);
      return result == true;
    } catch (e) {
      if (kDebugMode) {
        print('Failed to show pet overlay: $e');
      }
      return false;
    }
  }

  static Future<bool> playPetAction(String action, {bool loop = true}) async {
    try {
      final result = await _channel.invokeMethod<bool>('playPetAction', {
        'action': action,
        'loop': loop,
      }).timeout(_invokeTimeout);
      return result == true;
    } catch (e) {
      if (kDebugMode) {
        print('Failed to play pet action: $e');
      }
      return false;
    }
  }

  static Future<bool> hidePetOverlay() async {
    try {
      final result = await _channel.invokeMethod<bool>('hidePetOverlay').timeout(_invokeTimeout);
      return result == true;
    } catch (e) {
      if (kDebugMode) {
        print('Failed to hide pet overlay: $e');
      }
      return false;
    }
  }

  static Future<bool> isPetOverlayShowing() async {
    try {
      final result = await _channel.invokeMethod<bool>('isPetOverlayShowing').timeout(_invokeTimeout);
      return result == true;
    } catch (e) {
      if (kDebugMode) {
        print('Failed to query pet overlay state: $e');
      }
      return false;
    }
  }

  static Future<Map<String, dynamic>> getPetOverlayState() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'getPetOverlayState',
      ).timeout(_invokeTimeout);
      return Map<String, dynamic>.from(result ?? const {});
    } catch (e) {
      if (kDebugMode) {
        print('Failed to get pet overlay state: $e');
      }
      return const {};
    }
  }
}
