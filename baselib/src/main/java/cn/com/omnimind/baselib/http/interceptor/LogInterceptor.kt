package cn.com.omnimind.baselib.http.interceptor

import cn.com.omnimind.baselib.util.OmniLog
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

class LogInterceptor : Interceptor {
    private val loggingInterceptor: HttpLoggingInterceptor

    init {
        loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()


        // 打印请求头
        OmniLog.i("OkHttp", "Sending headers: ${request.headers}")
        
        // 打印请求行信息
        OmniLog.i("OkHttp", "Sending request: ${request.method} ${request.url} ")
        
        // 打印请求体（入参）
        val requestBody = request.body
        if (requestBody != null) {
            try {
                val buffer = okio.Buffer()
                requestBody.writeTo(buffer)
                val requestBodyString = buffer.readUtf8()
                OmniLog.i("OkHttp", "Request body: $requestBodyString")
            } catch (e: Exception) {
                OmniLog.e("OkHttp", "Failed to log request body: ${e.message}")
            }
        }
        
        val startTime = System.currentTimeMillis()
        val response = chain.proceed(request)
        val endTime = System.currentTimeMillis()

        // 检查是否为文件下载请求，避免读取大文件内容导致内存溢出
        val contentType = response.header("Content-Type", "")
        val contentLength = response.header("Content-Length", "-1")?.toLongOrNull() ?: -1
        
        // 如果是大文件或者APK下载，则不读取响应体内容
        val isLargeFileDownload = contentLength > 10 * 1024 * 1024 || // 大于10MB
                contentType?.contains("application/octet-stream") == true ||
                contentType?.contains("application/vnd.android.package-archive") == true ||
                request.url.toString().endsWith(".apk", ignoreCase = true)
        
        val responseBodyString = if (isLargeFileDownload) {
            "[File Download - Content not logged to prevent memory overflow]"
        } else {
            try {
                response.peekBody(Long.MAX_VALUE).string()
            } catch (e: IllegalStateException) {
                // 如果响应体已经被关闭，则捕获异常并返回提示信息
                "Response body already closed or consumed"
            } catch (e: Exception) {
                "Error reading response body: ${e.message}"
            }
        }
        
        OmniLog.i(
            "OkHttp",
            """
    Received response for ${response.request.url} in ${endTime - startTime}ms
    Status: ${response.code}
    Response body: $responseBodyString
    """.trimIndent()
        )
        return response
    }
}