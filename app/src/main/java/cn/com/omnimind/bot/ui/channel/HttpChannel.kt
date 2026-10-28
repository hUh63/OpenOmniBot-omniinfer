package cn.com.omnimind.bot.ui.channel

import cn.com.omnimind.baselib.http.OkHttpManager
import cn.com.omnimind.baselib.util.OmniLog
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class HttpChannel {

    val CHANNEL_NAME = "cn.com.omnimind.bot/network"
    private val TAG = "HttpChannel"
    private var channel: MethodChannel? = null
    private var mainJob:CoroutineScope= CoroutineScope(SupervisorJob()+Dispatchers.IO)

    fun setChannel(flutterEngine: FlutterEngine) {
        channel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_NAME
        )
        channel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "sendRequest" -> {
                    handleSendRequest( call, result)
                }

                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun handleSendRequest(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        try {
            val arguments = call.arguments as? Map<*, *>
            val url = arguments?.get("url") as? String
            val method = arguments?.get("method") as? String ?: "GET"
            val headers = arguments?.get("headers") as? Map<*, *>
            val params = arguments?.get("params") as? Map<*, *>
            val body = arguments?.get("body") as? String

            val filePaths = arguments?.get("filePaths") as? List<*>

            if (url.isNullOrEmpty()) {
                result.error("INVALID_URL", "URL is required", null)
                return
            }

            // 在IO线程中执行网络请求
            mainJob.launch(Dispatchers.IO) {
                try {
                    val request = buildRequest(
                        url,
                        method,
                        headers,
                        params,
                        body,
                        filePaths
                    )
                    val response = OkHttpManager.enqueue(request)

                    // use{}：无论成功失败都关掉连接；body 有上限，避免大响应体
                    // （日志/工作区预览/导出）整个进内存再被 codec 复制一遍 → OOM。
                    response.use { resp ->
                        val responseBody = readResponseBody(resp)
                        val responseHeaders = mutableMapOf<String, String>()
                        resp.headers.forEach { (name, value) ->
                            responseHeaders[name] = value
                        }

                        val responseMap = mapOf(
                            "statusCode" to resp.code,
                            "body" to responseBody,
                            "headers" to responseHeaders
                        )

                        withContext(Dispatchers.Main) {
                            result.success(responseMap)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    OmniLog.e(TAG, "Network request failed", e)
                    withContext(Dispatchers.Main) {
                        result.error("NETWORK_ERROR", e.message, e.toString())
                    }
                }
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "Failed to parse request parameters", e)
            result.error("INVALID_PARAMETERS", e.message, e.toString())
        }
    }

    /** 单个 HTTP 响应最多允许进内存的字节数；超出截断并显式标记。 */
    private val MAX_RESPONSE_BYTES = 4L * 1024 * 1024

    private fun readResponseBody(response: okhttp3.Response): String? {
        val body = response.body ?: return null
        val contentLength = body.contentLength()
        if (contentLength in 0..MAX_RESPONSE_BYTES) {
            return body.string()
        }
        val buffer = okio.Buffer()
        body.source().use { source ->
            var remaining = MAX_RESPONSE_BYTES
            while (remaining > 0) {
                val read = source.read(buffer, minOf(remaining, 64L * 1024))
                if (read == -1L) break
                remaining -= read
            }
        }
        return buffer.readUtf8() + "\n…[truncated at $MAX_RESPONSE_BYTES bytes]"
    }

    private fun mediaTypeFor(file: File) = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "txt", "md", "log", "json", "csv", "xml" -> "text/plain"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }.toMediaTypeOrNull()

    private fun buildRequest(
        url: String,
        method: String,
        headers: Map<*, *>?,
        params: Map<*, *>?,
        body: String?,
        filePaths: List<*>?
    ): Request {
        val builder = OkHttpManager.newBuilder()
            .url(url)

        // ✅ 处理文件上传
        if (filePaths != null && filePaths.isNotEmpty() && isMultipartContent(headers)) {
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
            
            filePaths.forEach { path ->
                if (path is String) {
                    val file = File(path)
                    if (file.exists()) {
                        // 原来所有文件都硬写成 image/*：pdf/zip/docx 附件会被服务端
                        // 按图片解析而静默失败。
                        val requestBody = file.asRequestBody(mediaTypeFor(file))
                        multipartBuilder.addFormDataPart(
                            "files",
                            file.name,
                            requestBody
                        )
                    }
                }
            }
            
            // multipart 分支原来无条件 post()：PUT/PATCH 上传会被改成 POST。
            val multipartBody = multipartBuilder.build()
            when (method.uppercase()) {
                "PUT" -> builder.put(multipartBody)
                "PATCH" -> builder.patch(multipartBody)
                else -> builder.post(multipartBody)
            }
        } else {
            // 添加请求方法
            when (method.uppercase()) {
                "GET" -> builder.get()
                "POST" -> builder.post()
                "PUT" -> builder.put()
                "DELETE" -> builder.delete()
                "PATCH" -> builder.patch()
            }

            // 添加请求体
            if (body != null) {
                when {
                    isJsonContent(headers) -> builder.setJsonBody(body)
                    else -> builder.setRequestBody(
                        okhttp3.RequestBody.create(
                            "text/plain".toMediaTypeOrNull(),
                            body
                        )
                    )
                }
            }
        }

        // 添加头部信息
        headers?.forEach { (key, value) ->
            if (key is String && value is String) {
                builder.addHeader(key, value)
            }
        }

        // 添加查询参数
        params?.forEach { (key, value) ->
            if (key is String && value is String) {
                builder.addParam(key, value)
            }
        }

        return builder.build()
    }

    // ✅ 新增：检查是否为 multipart 请求
    private fun isMultipartContent(headers: Map<*, *>?): Boolean {
        headers?.forEach { (key, value) ->
            if (key is String && value is String && key.equals("content-type", ignoreCase = true)) {
                return value.contains("multipart/form-data")
            }
        }
        return false
    }

    private fun isJsonContent(headers: Map<*, *>?): Boolean {
        headers?.forEach { (key, value) ->
            if (key is String && value is String && key.equals("content-type", ignoreCase = true)) {
                return value.contains("application/json")
            }
        }
        return false
    }

    fun clear() {
        channel?.setMethodCallHandler(null)
        channel = null
    }
}
