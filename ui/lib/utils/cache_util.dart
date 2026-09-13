import '../models/app_icons.dart';
import '../models/paged_messages_result.dart';
import '../services/cache.dart';

class CacheUtil {
  /// 缓存字符串值到MMKV
  /// [key] 键
  /// [value] 要缓存的字符串值
  static cacheString(String key, String value) async {
    await cacheEvent.invokeMethod("doMMKVEncodeString", {
      "key": key,
      "value": value,
    });
  }

  /// 缓存布尔值到MMKV
  /// [key] 键
  /// [value] 要缓存的布尔值
  static cacheBool(String key, bool value) async {
    await cacheEvent.invokeMethod("doMMKVEncodeBool", {
      "key": key,
      "value": value,
    });
  }

  /// 缓存整数值到MMKV
  /// [key] 键
  /// [value] 要缓存的整数值
  static cacheInt(String key, int value) async {
    await cacheEvent.invokeMethod("doMMKVEncodeInt", {
      "key": key,
      "value": value,
    });
  }

  /// 缓存双精度浮点数值到MMKV
  /// [key] 键
  /// [value] 要缓存的双精度浮点数值
  static cacheDouble(String key, double value) async {
    await cacheEvent.invokeMethod("doMMKVEncodeDouble", {
      "key": key,
      "value": value,
    });
  }

  /// 从MMKV获取字符串值
  /// [key] 键
  /// [defaultValue] 默认值，当键不存在时返回
  /// 返回对应的字符串值或默认值
  static Future<String> getString(
    String key, {
    String defaultValue = "",
  }) async {
    return await cacheEvent.invokeMethod("doMMKVDecodeString", {
      "key": key,
      "defaultValue": defaultValue,
    });
  }

  /// 从MMKV获取布尔值
  /// [key] 键
  /// [defaultValue] 默认值，当键不存在时返回
  /// 返回对应的布尔值或默认值
  static Future<bool> getBool(String key, {bool defaultValue = false}) async {
    return await cacheEvent.invokeMethod("doMMKVDecodeBoole", {
      "key": key,
      "defaultValue": defaultValue,
    });
  }

  /// 从MMKV获取整数值
  /// [key] 键
  /// [defaultValue] 默认值，当键不存在时返回
  /// 返回对应的整数值或默认值
  static Future<int> getInt(String key, {int defaultValue = 0}) async {
    final result = await cacheEvent.invokeMethod("doMMKVDecodeInt", {
      "key": key,
      "defaultValue": defaultValue,
    });
    if (result is int) {
      return result;
    }
    if (result is num) {
      return result.toInt();
    }
    return defaultValue;
  }

  /// 从MMKV获取双精度浮点数值
  /// [key] 键
  /// [defaultValue] 默认值，当键不存在时返回
  /// 返回对应的双精度浮点数值或默认值
  static Future<String> getDouble(
    String key, {
    double defaultValue = 0.0,
  }) async {
    return await cacheEvent.invokeMethod("doMMKVDecodeDouble", {
      "key": key,
      "defaultValue": defaultValue,
    });
  }

  // AppIcons相关方法
  static Future<AppIcons?> getAppIconByPackageName(String packageName) async {
    final Map<dynamic, dynamic>? result = await cacheEvent.invokeMethod(
      "getAppIconByPackageName",
      {"packageName": packageName},
    );
    return result != null ? AppIcons.fromMap(result) : null;
  }

  static Future<List<AppIcons>> getAppIconsByPackageNames(
    List<String> packageNames,
  ) async {
    final List<dynamic>? result = await cacheEvent.invokeMethod(
      "getAppIconsByPackageNames",
      {"packageNames": packageNames},
    );
    return result?.map((e) => AppIcons.fromMap(e)).toList() ?? [];
  }

  static Future<bool> insertAppIcon({
    required String appName,
    required String packageName,
    required String iconBase64,
    String iconPath = "",
  }) async {
    try {
      final result = await cacheEvent.invokeMethod("insertAppIcon", {
        "appName": appName,
        "packageName": packageName,
        "icon_base64": iconBase64,
        "icon_path": iconPath,
      });
      return result == true;
    } catch (e) {
      return false;
    }
  }

  // Message相关方法
  static Future<int> insertMessage({
    required String messageId,
    required int type,
    required int user,
    required String content,
  }) async {
    return await cacheEvent.invokeMethod("insertMessage", {
      "messageId": messageId,
      "type": type,
      "user": user,
      "content": content,
    });
  }

  static Future<bool> updateMessage({
    required int id,
    required String messageId,
    required int type,
    required int user,
    required String content,
    required int createdAt,
  }) async {
    return await cacheEvent.invokeMethod("updateMessage", {
      "id": id,
      "messageId": messageId,
      "type": type,
      "user": user,
      "content": content,
      "createdAt": createdAt,
    });
  }

  static Future<Map<String, dynamic>?> getMessageById(int id) async {
    final dynamic result = await cacheEvent.invokeMethod("getMessageById", {
      "id": id,
    });
    if (result != null) {
      return result as Map<String, dynamic>;
    } else {
      return null;
    }
  }

  static Future<PagedMessagesResult> getMessagesByPage({
    required int page,
    required int pageSize,
  }) async {
    final Map<dynamic, dynamic> result = await cacheEvent.invokeMethod(
      "getMessagesByPage",
      {"page": page, "pageSize": pageSize},
    );
    return PagedMessagesResult.fromMap(result);
  }

  static Future<bool> deleteMessageById(int id) async {
    return await cacheEvent.invokeMethod("deleteMessageById", {"id": id});
  }

  static Future<bool> deleteAllMessages() async {
    return await cacheEvent.invokeMethod("deleteAllMessages");
  }
}
