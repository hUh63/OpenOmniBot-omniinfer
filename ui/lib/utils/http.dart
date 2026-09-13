import 'dart:convert';

import 'package:flutter/services.dart';

import '../services/http_handler.dart';

class HttpUtil {
  /// 发送网络请求
  static Future<HttpResponse> sendRequest({
    required String url,
    String method = 'GET',
    Map<String, String>? headers,
    Map<String, String>? params,
    dynamic body,
  }) async {
    try {
      final Map<String, dynamic> arguments = {
        'url': url,
        'method': method,
        'headers': headers,
        'params': params,
      };

      // 处理请求体
      if (body != null) {
        if (body is String) {
          arguments['body'] = body;
        } else if (body is Map) {
          arguments['body'] = jsonEncode(body);
        } else {
          arguments['body'] = body.toString();
        }
      }

      final response = await httpChannel.invokeMethod('sendRequest', arguments);
      return HttpResponse.fromJson(response);
    } on PlatformException catch (e) {
      throw HttpException(e.code, e.message ?? 'Unknown error');
    } catch (e) {
      throw HttpException('UNKNOWN_ERROR', e.toString());
    }
  }

  /// GET请求
  static Future<HttpResponse> get(
    String url, {
    Map<String, String>? headers,
    Map<String, String>? params,
  }) {
    return sendRequest(
      url: url,
      method: 'GET',
      headers: headers,
      params: params,
    );
  }

  /// POST请求
  static Future<HttpResponse> post(
    String url, {
    Map<String, String>? headers,
    Map<String, String>? params,
    dynamic body,
  }) {
    return sendRequest(
      url: url,
      method: 'POST',
      headers: headers,
      params: params,
      body: body,
    );
  }

  /// PUT请求
  static Future<HttpResponse> put(
    String url, {
    Map<String, String>? headers,
    Map<String, String>? params,
    dynamic body,
  }) {
    return sendRequest(
      url: url,
      method: 'PUT',
      headers: headers,
      params: params,
      body: body,
    );
  }

  /// DELETE请求
  static Future<HttpResponse> delete(
    String url, {
    Map<String, String>? headers,
    Map<String, String>? params,
  }) {
    return sendRequest(
      url: url,
      method: 'DELETE',
      headers: headers,
      params: params,
    );
  }

  /// PATCH请求
  static Future<HttpResponse> patch(
    String url, {
    Map<String, String>? headers,
    Map<String, String>? params,
    dynamic body,
  }) {
    return sendRequest(
      url: url,
      method: 'PATCH',
      headers: headers,
      params: params,
      body: body,
    );
  }
}

/// HTTP响应类
class HttpResponse {
  final int statusCode;
  final String? body;
  final Map<String, String> headers;

  HttpResponse({required this.statusCode, this.body, required this.headers});

  factory HttpResponse.fromJson(Map<dynamic, dynamic> json) {
    return HttpResponse(
      statusCode: json['statusCode'] as int,
      body: json['body'] as String?,
      headers: Map<String, String>.from(
        json['headers'] as Map<dynamic, dynamic>,
      ),
    );
  }

  /// 将响应体解析为JSON对象
  dynamic toJson() {
    if (body == null) return null;
    try {
      return jsonDecode(body!);
    } catch (e) {
      return null;
    }
  }

  @override
  String toString() {
    return 'HttpResponse{statusCode: $statusCode, body: $body, headers: $headers}';
  }
}

/// HTTP异常类
class HttpException implements Exception {
  final String code;
  final String message;

  HttpException(this.code, this.message);

  @override
  String toString() {
    return 'HttpException{code: $code, message: $message}';
  }
}
