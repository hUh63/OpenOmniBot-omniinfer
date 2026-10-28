package cn.com.omnimind.baselib.http

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.VisibleForTesting
import cn.com.omnimind.baselib.http.interceptor.CommonParamsInterceptor
import cn.com.omnimind.baselib.http.interceptor.HeaderInterceptor
import cn.com.omnimind.baselib.service.DeviceInfoService
import cn.com.omnimind.baselib.util.APPPackageUtil
import cn.com.omnimind.baselib.util.ContentEndpointSecurity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("StaticFieldLeak")
object OkHttpManager {
    @Volatile
    private var mInstance: OkHttpClient? = null
    private const val DEFAULT_TIMEOUT: Long = 60

    val BASE_HTTP_URL: String
        get() {
            return getBaseUrl()
        }

    /**
     * 使用反射获取BASE_HTTP_URL
     */
    private fun getBaseUrl(): String {
        val baseUrl = getDefaultBaseUrl()
        if (baseUrl.isBlank()) {
            Log.w("OkHttpManager", "BASE_URL is empty; backend features are disabled in open-source mode")
        } else {
            Log.d("OkHttpManager", "Using configured base URL")
        }
        return baseUrl
    }

    private fun getDefaultBaseUrl(): String {
        try {
            // 明确指定 app 模块的 BuildConfig
            val appBuildConfig = Class.forName("cn.com.omnimind.bot.BuildConfig")
            val baseUrl = appBuildConfig.getField("BASE_URL").get(null) as String
            return baseUrl.trim().trimEnd('/')
        } catch (e: Exception) {
            return ""
        }
    }

    fun getAppVersionHeaders(): Map<String, String> {
        val appVersionInfo = DeviceInfoService.getAppVersion(BaseApplication.instance)
        return buildAppVersionHeaders(
            appVersionInfo = appVersionInfo,
            isDebug = APPPackageUtil.isAppDebug()
        )
    }

    @VisibleForTesting
    internal fun buildAppVersionHeaders(
        appVersionInfo: Map<String, Any>,
        isDebug: Boolean
    ): Map<String, String> {
        return mapOf(
            "App-Version-Name" to (appVersionInfo["versionName"]?.toString() ?: "1.0"),
            "App-Version-Code" to (appVersionInfo["versionCode"]?.toString() ?: "1"),
            "App-Platform" to (appVersionInfo["platform"]?.toString() ?: "android"),
            "App-IsDebug" to isDebug.toString()
        )
    }

    /** 真实 versionName；拿不到（早期初始化/单元测试）时退回 1.0.0。 */
    private fun resolveAppVersionName(): String {
        return runCatching {
            val appBuildConfig = Class.forName("cn.com.omnimind.bot.BuildConfig")
            appBuildConfig.getField("VERSION_NAME").get(null) as String
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "1.0.0"
    }

    private fun getInstance(): OkHttpClient {
        return mInstance ?: synchronized(this) {
            mInstance ?: buildClient().also { mInstance = it }
        }
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(HeaderInterceptor())
            .addInterceptor(
                // 版本号取真实值：硬编码 1.0.0 会让服务端按错误版本下发配置/灰度。
                CommonParamsInterceptor(appVersion = resolveAppVersionName())
            )
//            .addInterceptor(LogInterceptor())

        return builder.build()
    }

    /**
     * Creates a client for requests that carry prompts, files, workspace data, or model output.
     * Redirects are deliberately disabled: a destination approved by the user must not be able to
     * move sensitive content or authorization headers to a different endpoint.
     */
    fun sensitiveContentClient(
        client: OkHttpClient,
        allowInsecureLoopback: Boolean = false,
        allowInsecureTransport: Boolean = false,
    ): OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            try {
                ContentEndpointSecurity.requireSafe(
                    rawUrl = chain.request().url.toString(),
                    allowInsecureLoopback = allowInsecureLoopback,
                    allowInsecureTransport = allowInsecureTransport,
                )
            } catch (_: IllegalArgumentException) {
                throw IOException("Sensitive content endpoint was rejected.")
            }
            chain.proceed(chain.request())
        }
        .build()

    /** Revalidates the final immutable request immediately before a network call is created. */
    fun sensitiveContentCall(
        client: OkHttpClient,
        request: Request,
        allowInsecureLoopback: Boolean = false,
        allowInsecureTransport: Boolean = false,
    ): Call {
        ContentEndpointSecurity.requireSafe(
            rawUrl = request.url.toString(),
            allowInsecureLoopback = allowInsecureLoopback,
            allowInsecureTransport = allowInsecureTransport,
        )
        return sensitiveContentClient(
            client = client,
            allowInsecureLoopback = allowInsecureLoopback,
            allowInsecureTransport = allowInsecureTransport,
        ).newCall(request)
    }

    /** Applies the same final-request gate to SSE, whose factory otherwise owns call creation. */
    fun sensitiveContentEventSource(
        client: OkHttpClient,
        request: Request,
        listener: EventSourceListener,
        allowInsecureLoopback: Boolean = false,
    ): EventSource {
        ContentEndpointSecurity.requireSafe(
            rawUrl = request.url.toString(),
            allowInsecureLoopback = allowInsecureLoopback,
        )
        return EventSources.createFactory(
            sensitiveContentClient(client, allowInsecureLoopback)
        ).newEventSource(request, listener)
    }

    /** Applies the same final-request gate to a WebSocket handshake. */
    fun sensitiveContentWebSocket(
        client: OkHttpClient,
        request: Request,
        listener: WebSocketListener,
        allowInsecureLoopback: Boolean = false,
    ): WebSocket {
        ContentEndpointSecurity.requireSafe(
            rawUrl = request.url.toString(),
            allowInsecureLoopback = allowInsecureLoopback,
        )
        return sensitiveContentClient(client, allowInsecureLoopback)
            .newWebSocket(request, listener)
    }

    // 执行请求
    fun execute(request: Request): Response {
        return getInstance().newCall(request).execute()
    }

    // 异步执行请求 - 协程方式
    suspend fun enqueue(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = getInstance().newCall(request)

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {


            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code == 429) {
                    // 不关掉会把连接一直占着：限流时会连续触发，连接池/socket 一路泄漏，
                    // 之后所有请求都排队等连接（表现为「一直转圈最后超时」）。
                    runCatching { response.close() }
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(Http429Exception("小万忙不过来了，等会儿再试试吧!"))
                    }
                    return
                }
                if (continuation.isCancelled) {
                    runCatching { response.close() }
                    return
                }
                continuation.resume(response)
            }
        })
    }

    private val doTask = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * SSE 专用 client：必须复用。
     *
     * 原来每次 enqueueWithStream 都 new 一个 OkHttpClient —— 每个实例自带独立
     * Dispatcher 线程池和空连接池（不共享、也不会被 shutdown），配合 60s readTimeout，
     * 流一多就是线程池泄漏 + 每次重新 DNS/TCP/TLS（首字延迟越来越长）。
     */
    private val streamClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            // 保留 60s：流式期间服务端会发心跳；设 0 会在服务端静默僵死时永久挂住。
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // 流式执行请求 - 协程方式，基于SSE实现
    suspend fun enqueueWithStream(request: Request, event: EventSourceListener): EventSource {

        return EventSources.createFactory(streamClient)
            .newEventSource(request, object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    event.onOpen(eventSource, response)
                }

                override fun onEvent(
                    eventSource: EventSource, id: String?, type: String?, data: String
                ) {
                    event.onEvent(eventSource, id, type, data)
                }

                override fun onClosed(eventSource: EventSource) {
                    event.onClosed(eventSource)
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    // 429错误通过onFailure回调传递，由调用方检查response?.code处理
                    event.onFailure(eventSource, t, response)
                }
            })
    }

    // 创建Builder实例
    fun newBuilder(): RequestBuilder {
        return RequestBuilder()
    }

    // Builder类用于链式调用
    class RequestBuilder {
        private var url: String? = null
        private var method: String = "GET"
        private var params: MutableMap<String, String>? = null
        private var formParams: MutableMap<String, String>? = null
        private var jsonBody: String? = null
        private var requestBody: RequestBody? = null
        private var headers: MutableMap<String, String>? = null

        fun url(url: String): RequestBuilder {
            this.url = url
            return this
        }

        fun addParam(key: String, value: String): RequestBuilder {
            if (params == null) {
                params = mutableMapOf()
            }
            params!![key] = value
            return this
        }

        fun addParams(params: Map<String, String>): RequestBuilder {
            if (this.params == null) {
                this.params = mutableMapOf()
            }
            this.params!!.putAll(params)
            return this
        }

        fun addFormParam(key: String, value: String): RequestBuilder {
            if (formParams == null) {
                formParams = mutableMapOf()
            }
            formParams!![key] = value
            return this
        }

        fun addFormParams(params: Map<String, String>): RequestBuilder {
            if (this.formParams == null) {
                this.formParams = mutableMapOf()
            }
            this.formParams!!.putAll(params)
            return this
        }

        fun setJsonBody(json: String): RequestBuilder {
            this.jsonBody = json
            return this
        }

        fun setRequestBody(requestBody: RequestBody): RequestBuilder {
            this.requestBody = requestBody
            return this
        }

        fun addHeader(key: String, value: String): RequestBuilder {
            if (headers == null) {
                headers = mutableMapOf()
            }
            headers!![key] = value
            return this
        }

        fun addHeaders(headers: Map<String, String>): RequestBuilder {
            if (this.headers == null) {
                this.headers = mutableMapOf()
            }
            this.headers!!.putAll(headers)
            return this
        }

        fun get(): RequestBuilder {
            this.method = "GET"
            return this
        }

        fun post(): RequestBuilder {
            this.method = "POST"
            return this
        }

        fun put(): RequestBuilder {
            this.method = "PUT"
            return this
        }

        fun delete(): RequestBuilder {
            this.method = "DELETE"
            return this
        }

        fun patch(): RequestBuilder {
            this.method = "PATCH"
            return this
        }

        fun build(): Request {
            if (url == null) {
                throw IllegalArgumentException("URL must be set")
            }

            val requestBuilder = Request.Builder()

            // 添加头部信息
            headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // 设置URL
            requestBuilder.url(url!!)

            // 根据方法类型构建请求体
            when (method) {
                "GET" -> {
                    val httpUrlBuilder = url!!.toHttpUrlOrNull()?.newBuilder()
                        ?: throw IllegalArgumentException("Invalid URL")

                    params?.forEach { (key, value) ->
                        httpUrlBuilder.addQueryParameter(key, value)
                    }

                    requestBuilder.url(httpUrlBuilder.build()).get()
                }

                "POST" -> {
                    when {
                        jsonBody != null -> {
                            val body =
                                jsonBody!!.toRequestBody("application/json; charset=utf-8".toMediaType())
                            requestBuilder.post(body)
                        }

                        formParams != null -> {
                            val formBodyBuilder = FormBody.Builder()
                            formParams!!.forEach { (key, value) ->
                                formBodyBuilder.add(key, value)
                            }
                            requestBuilder.post(formBodyBuilder.build())
                        }

                        requestBody != null -> {
                            requestBuilder.post(requestBody!!)
                        }

                        else -> {
                            requestBuilder.post("".toRequestBody("text/plain".toMediaType()))
                        }
                    }
                }

                "PUT" -> {
                    if (requestBody != null) {
                        requestBuilder.put(requestBody!!)
                    } else {
                        requestBuilder.put("".toRequestBody("text/plain".toMediaType()))
                    }
                }

                "DELETE" -> {
                    requestBuilder.delete()
                }

                "PATCH" -> {
                    if (requestBody != null) {
                        requestBuilder.patch(requestBody!!)
                    } else {
                        requestBuilder.patch("".toRequestBody("text/plain".toMediaType()))
                    }
                }
            }

            return requestBuilder.build()
        }

        companion object {
            fun newInstance(): RequestBuilder {
                return RequestBuilder()
            }
        }
    }

    // 文件下载回调接口
    interface DownloadCallback {
        fun onProgress(progress: Long, total: Long)
        fun onSuccess(file: File)
        fun onError(e: Exception)
    }

    // 下载文件
    fun downloadFile(url: String, destinationFile: File, callback: DownloadCallback?) {
        val request = Request.Builder()
            .url(url)
            .build()

        getInstance().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback?.onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                // use{} 保证 Response/流一定被关闭：原来非 2xx 直接 return 会漏连接，
                // 中途异常时 input/output 也没关（fd 泄漏 + 半截文件）。
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        callback?.onError(IOException("Unexpected code $resp"))
                        return
                    }
                    val responseBody = resp.body
                    if (responseBody == null) {
                        callback?.onError(IOException("Response body is null"))
                        return
                    }
                    val totalBytes = responseBody.contentLength()
                    var downloadedBytes = 0L
                    try {
                        responseBody.byteStream().use { inputStream ->
                            FileOutputStream(destinationFile).use { outputStream ->
                                val buffer = ByteArray(64 * 1024)
                                var bytesRead: Int
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead
                                    if (callback != null && totalBytes > 0) {
                                        callback.onProgress(downloadedBytes, totalBytes)
                                    }
                                    // 这里原来每 1MB 调一次 System.gc()：整进程 STW，
                                    // 下载越大卡得越明显，后台 agent 一起抖。
                                }
                                outputStream.flush()
                            }
                        }
                        callback?.onSuccess(destinationFile)
                    } catch (e: Exception) {
                        // 别把半截文件留在磁盘上冒充「已下载」。
                        runCatching { destinationFile.delete() }
                        callback?.onError(e)
                    }
                }
            }
        })
    }

    // 带进度监听的文件下载扩展函数
    suspend fun downloadFileWithProgress(
        url: String,
        destinationFile: File,
        onProgress: ((progress: Long, total: Long) -> Unit)? = null,
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .build()

        val call = getInstance().newCall(request)

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (continuation.isCancelled) return

                    if (!resp.isSuccessful) {
                        if (!continuation.isCancelled) {
                            continuation.resumeWithException(IOException("Unexpected code $resp"))
                        }
                        return
                    }

                    val responseBody = resp.body
                    if (responseBody == null) {
                        if (!continuation.isCancelled) {
                            continuation.resumeWithException(IOException("Response body is null"))
                        }
                        return
                    }
                    val totalBytes = responseBody.contentLength()
                    var downloadedBytes = 0L
                    try {
                        responseBody.byteStream().use { inputStream ->
                            FileOutputStream(destinationFile).use { outputStream ->
                                val buffer = ByteArray(64 * 1024)
                                var bytesRead: Int
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead
                                    if (onProgress != null && totalBytes > 0) {
                                        onProgress(downloadedBytes, totalBytes)
                                    }
                                }
                                outputStream.flush()
                            }
                        }
                        if (!continuation.isCancelled) {
                            continuation.resume(Result.success(destinationFile))
                        }
                    } catch (e: Exception) {
                        runCatching { destinationFile.delete() }
                        if (!continuation.isCancelled) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            }
        })
    }

}

class Http429Exception(override val message: String) : Exception(message)
