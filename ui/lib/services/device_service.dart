import 'package:flutter/services.dart';

/// 设备信息服务
class DeviceService {
  static const MethodChannel _methodChannel = MethodChannel('device_info');

  /// 获取Android设备ID
  static Future<String?> getAndroidId() async {
    try {
      final String? androidId = await _methodChannel.invokeMethod('getAndroidId');
      return androidId;
    } on PlatformException catch (e) {
      print('获取Android ID失败: ${e.message}');
      return null;
    } catch (e) {
      print('获取Android ID时发生未知错误: $e');
      return null;
    }
  }

  /// 获取设备信息
  static Future<Map<String, dynamic>?> getDeviceInfo() async {
    try {
      final result = await _methodChannel.invokeMethod('getDeviceInfo');
      return _mapFromResult(result);
    } on PlatformException catch (e) {
      print('获取设备信息失败: ${e.message}');
      return null;
    } catch (e) {
      print('获取设备信息时发生未知错误: $e');
      return null;
    }
  }

  /// 获取设备IP地址
  static Future<String?> getIpAddress() async {
    try {
      final String? ipAddress = await _methodChannel.invokeMethod('getIpAddress');
      return ipAddress;
    } on PlatformException catch (e) {
      print('获取IP地址失败: ${e.message}');
      return null;
    } catch (e) {
      print('获取IP地址时发生未知错误: $e');
      return null;
    }
  }

  /// 获取应用版本信息（版本号、平台类型）
  static Future<Map<String, dynamic>?> getAppVersion() async {
    try {
      final result = await _methodChannel.invokeMethod('getAppVersion');
      if (result == null) {
        return null;
      }
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      print('获取应用版本失败: ${e.message}');
      return null;
    } catch (e) {
      print('获取应用版本时发生未知错误: $e');
      return null;
    }
  }

  /// 将从平台调用返回的动态结果规范化为 Map<String, dynamic>
  static Map<String, dynamic>? _mapFromResult(dynamic result) {
    if (result == null) return null;
    if (result is Map) {
      try {
        return result.map((key, value) => MapEntry(key?.toString() ?? '', value));
      } catch (e) {
        print('无法将结果转换为 Map<String, dynamic>: $e');
        return null;
      }
    }
    return null;
  }
}
