package cn.com.omnimind.bot.ui.channel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import cn.com.omnimind.bot.omniinfer.OmniInferLocalRuntime
import cn.com.omnimind.bot.omniinfer.OmniInferLiteRtModelsManager
import cn.com.omnimind.bot.omniinfer.OmniInferMnnModelsManager
import cn.com.omnimind.bot.omniinfer.OmniInferModelsManager
import cn.com.omnimind.bot.omniinfer.OmniInferQnnModelsManager
import com.omniinfer.server.OmniInferServer
import com.tencent.mmkv.MMKV
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import cn.com.omnimind.baselib.util.OmniLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MnnLocalModelsChannel {
    companion object {
        private const val TAG = "MnnLocalModelsChannel"
        private const val METHOD_CHANNEL = "cn.com.omnimind.bot/MnnLocalModels"
        private const val EVENT_CHANNEL = "cn.com.omnimind.bot/MnnLocalModelsEvents"
        private const val ERROR_CODE = "MNN_LOCAL_ERROR"
        private const val MMKV_BACKEND_KEY = "omniinfer_selected_backend"
        private const val DEFAULT_BACKEND = OmniInferLocalRuntime.BACKEND_LLAMA_CPP

        private const val REQUEST_CODE_IMPORT_FILE = 39122
        private const val REQUEST_CODE_IMPORT_TREE = 39123

        private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val importHandler = Handler(Looper.getMainLooper())

        @Volatile
        private var pendingImportResult: MethodChannel.Result? = null
        @Volatile
        private var pendingImportBackend: String = DEFAULT_BACKEND
        /** 发起导入的时间：用来判断上一次导入是否已经永远不会回调了。 */
        @Volatile
        private var pendingImportAt: Long = 0L

        /**
         * 导入请求的保鲜期。用户在选择器里取消、或进程被系统回收后 Activity 不再回调
         * onActivityResult，这个槽位就会永久被占住 —— 之后每次 importModel 都返回
         * 「Another import is already in progress」，只能杀进程才能恢复。
         */
        private const val IMPORT_REQUEST_STALE_MS = 5 * 60 * 1000L

        fun onActivityResult(
            activity: Activity,
            requestCode: Int,
            resultCode: Int,
            data: Intent?,
        ): Boolean {
            if (requestCode != REQUEST_CODE_IMPORT_FILE && requestCode != REQUEST_CODE_IMPORT_TREE) {
                return false
            }

            val result = pendingImportResult
            val backend = pendingImportBackend
            pendingImportResult = null
            pendingImportBackend = DEFAULT_BACKEND
            pendingImportAt = 0L
            if (result == null) return true

            if (resultCode != Activity.RESULT_OK || data?.data == null) {
                result.success(mapOf("success" to false, "error" to "cancelled"))
                return true
            }

            val uri = data.data!!
            val isTree = requestCode == REQUEST_CODE_IMPORT_TREE

            importScope.launch {
                val importResult = runCatching {
                    if (isTree) {
                        OmniInferMnnModelsManager.importModelFromUri(activity, uri)
                    } else if (backend == OmniInferLocalRuntime.BACKEND_LITERT) {
                        OmniInferLiteRtModelsManager.importModelFromUri(activity, uri)
                    } else {
                        OmniInferModelsManager.importModelFromUri(activity, uri)
                    }
                }.getOrElse { e ->
                    mapOf("success" to false, "error" to (e.message ?: "unknown_error"))
                }
                importHandler.post { result.success(importResult) }
            }
            return true
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var context: Context? = null
    private var methodChannel: MethodChannel? = null
    private var eventChannel: EventChannel? = null
    private var eventSink: EventChannel.EventSink? = null
    private val backendMmkv: MMKV by lazy { MMKV.mmkvWithID("omniinfer_config") }
    private var preloadJob: Job? = null
    private val preloadMutex = Mutex()

    fun onCreate(context: Context) {
        this.context = context
        OmniInferModelsManager.setContext(context)
        OmniInferMnnModelsManager.setContext(context)
        OmniInferQnnModelsManager.setContext(context)
        OmniInferLiteRtModelsManager.setContext(context)
    }

    fun setChannel(flutterEngine: FlutterEngine) {
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL)
        methodChannel?.setMethodCallHandler(::handleMethodCall)

        eventChannel = EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
        eventChannel?.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                eventSink = events
                val dispatcher: (Map<String, Any?>) -> Unit = { payload ->
                    mainHandler.post { eventSink?.success(payload) }
                }
                OmniInferModelsManager.setEventDispatcher(dispatcher)
                OmniInferMnnModelsManager.setEventDispatcher(dispatcher)
                OmniInferQnnModelsManager.setEventDispatcher(dispatcher)
                OmniInferLiteRtModelsManager.setEventDispatcher(dispatcher)
                // Surfaces "排队中" while a second local request waits for the inference slot.
                OmniInferServer.queueObserver = { active, queued ->
                    dispatcher(
                        mapOf(
                            "type" to "inferenceQueue",
                            "active" to active,
                            "queued" to queued,
                        ),
                    )
                }
            }

            override fun onCancel(arguments: Any?) {
                eventSink = null
                OmniInferServer.queueObserver = null
                OmniInferModelsManager.setEventDispatcher(null)
                OmniInferMnnModelsManager.setEventDispatcher(null)
                OmniInferQnnModelsManager.setEventDispatcher(null)
                OmniInferLiteRtModelsManager.setEventDispatcher(null)
            }
        })
    }

    fun clear() {
        OmniInferServer.queueObserver = null
        OmniInferModelsManager.setEventDispatcher(null)
        OmniInferMnnModelsManager.setEventDispatcher(null)
        OmniInferQnnModelsManager.setEventDispatcher(null)
        OmniInferLiteRtModelsManager.setEventDispatcher(null)
        eventSink = null
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        eventChannel?.setStreamHandler(null)
        eventChannel = null
        // 挂起的导入请求必须在这里回收：它属于进程级静态状态，Flutter view 销毁后旧 Dart
        // 侧的 await 随 isolate 一起消失，但槽位仍被占着，新 isolate 再也发不起导入。
        pendingImportResult?.let { stale ->
            runCatching {
                stale.success(mapOf("success" to false, "error" to "cancelled"))
            }
        }
        pendingImportResult = null
        pendingImportBackend = DEFAULT_BACKEND
        pendingImportAt = 0L
        // NOTE: Do NOT call OmniInferModelsManager.clear() or OmniInferMnnModelsManager.clear()
        // here. Those methods cancel active downloads, but this clear() is called when the
        // Flutter view is destroyed (e.g. app goes to background). Downloads should continue
        // running. The event dispatcher is already disconnected above; it will be reconnected
        // when the channel is re-established via setChannel().
    }

    /**
     * Installed models across every backend. The per-backend calls only see their own directory
     * (models/llama, models/mnn, models/qnn, models/litert), so a model downloaded for one
     * backend vanished from the page as soon as another backend was selected.
     */
    private fun listAllInstalledModels(): List<Map<String, Any?>> {
        val merged = LinkedHashMap<String, Map<String, Any?>>()
        listOf(
            OmniInferModelsManager.listInstalledModels(),
            OmniInferMnnModelsManager.listInstalledModels(),
            OmniInferQnnModelsManager.listInstalledModels(),
            OmniInferLiteRtModelsManager.listInstalledModels(),
        ).forEach { models ->
            models.forEach { model ->
                val id = model["id"]?.toString()?.trim().orEmpty()
                if (id.isNotEmpty()) merged.putIfAbsent(id, model)
            }
        }
        return merged.values.toList()
    }

    /**
     * 只允许提交一次回包的 Result 包装。
     *
     * MethodChannel 的 Result 一旦 reply 过，再 reply 会抛
     * `IllegalStateException: Reply already submitted`；在协程里这个异常没人接住，
     * 直接冒成 FATAL（用户侧表现就是「点某个按钮闪退」）。这里把「可能被两条路径
     * 同时回包」的地方（同步分支 + runSuspend 的 catch + 外层 catch）统一收敛到
     * 一个原子标记上，先到先得，后来的静默丢弃并记日志。
     */
    private class OnceResult(
        private val delegate: MethodChannel.Result,
    ) : MethodChannel.Result {
        private val replied = AtomicBoolean(false)

        override fun success(result: Any?) {
            if (!replied.compareAndSet(false, true)) {
                OmniLog.w(TAG, "duplicate reply dropped: success()")
                return
            }
            // delegate 自己也可能抛（engine 已 detach 时 reply 会失败）；
            // 这些异常绝不能冒到协程里变成 FATAL。
            runCatching { delegate.success(result) }
                .onFailure { OmniLog.w(TAG, "reply success failed: ${it.message}") }
        }

        override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
            if (!replied.compareAndSet(false, true)) {
                OmniLog.w(TAG, "duplicate reply dropped: error($errorCode)")
                return
            }
            runCatching { delegate.error(errorCode, errorMessage, errorDetails) }
                .onFailure { OmniLog.w(TAG, "reply error failed: ${it.message}") }
        }

        override fun notImplemented() {
            if (!replied.compareAndSet(false, true)) {
                OmniLog.w(TAG, "duplicate reply dropped: notImplemented()")
                return
            }
            runCatching { delegate.notImplemented() }
                .onFailure { OmniLog.w(TAG, "reply notImplemented failed: ${it.message}") }
        }
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        // 同步分支（getApiAuth / saveApiAuth / getBackend / setBackend / getConfig /
        // setActiveModel ...）此前是裸调的：ensureContext() 未初始化、MMKV 异常、
        // provider 刷新抛错都会直接冒到 Flutter 的 MethodChannel handler —— 主线程
        // FATAL，同时 Dart 侧那次 invokeMethod 永远收不到回包（页面永久 loading）。
        val once = OnceResult(result)
        try {
            handleMethodCallInternal(call, once)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            once.error(
                ERROR_CODE,
                error.message ?: error.javaClass.simpleName,
                null,
            )
        }
    }

    private fun handleMethodCallInternal(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            // 这里必须显式 return：下面还有一段「按 backend 分派」的 when，
            // 少了 return 会把同一次调用再喂给 handleXxxCall，走到那里的
            // else -> result.notImplemented()，于是同一个 Result 被 reply 两次，
            // 抛 Reply already submitted（打开「模型市场 / 高级设置」时闪退）。
            "listAllInstalledModels" -> {
                runSuspend(result) { listAllInstalledModels() }
                return
            }

            "getApiAuth" -> {
                result.success(OmniInferLocalRuntime.apiAuthState())
                return
            }

            "saveApiAuth" -> {
                val args = (call.arguments as? Map<*, *>) ?: emptyMap<String, Any?>()
                result.success(OmniInferLocalRuntime.saveApiAuth(args))
                return
            }

            "getBackend" -> {
                result.success(getSelectedBackend())
                return
            }

            "setBackend" -> {
                val backend = OmniInferLocalRuntime.normalizeBackend(call.argument<String>("backend"))
                // 切后端必须先停掉已加载的引擎：原来只改一个 MMKV 字符串 —— 正在跑的
                // 引擎（getLoadedBackend）没变，页面却按新后端显示「运行中、端口一致」，
                // agent 的请求继续打在旧引擎上，而且没有任何回落。
                val previous = OmniInferLocalRuntime.getSelectedBackend()
                if (previous != backend) {
                    runCatching { OmniInferLocalRuntime.stop() }
                        .onFailure { error ->
                            OmniLog.w(
                                TAG,
                                "stop before backend switch failed: ${error.message}"
                            )
                        }
                    backendMmkv.encode(MMKV_BACKEND_KEY, backend)
                    OmniInferLocalRuntime.setSelectedBackend(backend)
                }
                result.success(backend)
                return
            }

            "importModel" -> {
                val activity = context as? Activity
                if (activity == null) {
                    result.error(ERROR_CODE, "Not attached to activity", null)
                    return
                }
                val now = System.currentTimeMillis()
                val staleImport = pendingImportResult?.takeIf {
                    now - pendingImportAt >= IMPORT_REQUEST_STALE_MS
                }
                if (staleImport != null) {
                    pendingImportResult = null
                    pendingImportBackend = DEFAULT_BACKEND
                    pendingImportAt = 0L
                    runCatching {
                        staleImport.success(mapOf("success" to false, "error" to "cancelled"))
                    }
                }
                if (pendingImportResult != null) {
                    result.error(ERROR_CODE, "Another import is already in progress", null)
                    return
                }
                pendingImportResult = result
                pendingImportAt = now
                try {
                    val backend = getSelectedBackend()
                    pendingImportBackend = backend
                    val isTreePicker = backend == OmniInferLocalRuntime.BACKEND_OMNIINFER_MNN
                    if (isTreePicker) {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT_TREE)
                    } else {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT_FILE)
                    }
                } catch (e: Exception) {
                    pendingImportResult = null
                    pendingImportBackend = DEFAULT_BACKEND
                    result.error(ERROR_CODE, e.message ?: "Failed to open file picker", null)
                }
                return
            }

            "preloadModel" -> {
                val modelId = call.argument<String>("modelId")?.trim().orEmpty()
                if (modelId.isEmpty()) {
                    result.success(mapOf("success" to false, "error" to "empty modelId"))
                    return
                }
                preloadJob?.cancel()
                preloadJob = scope.launch(Dispatchers.IO) {
                    var success = false
                    var error = ""
                    try {
                        preloadMutex.withLock {
                            ensureActive()
                            OmniLog.i(TAG, "[preloadModel] loading modelId=$modelId")
                            success = runCatching {
                                OmniInferModelsManager.ensureModelReady(modelId)
                            }.getOrDefault(false)
                            if (!success) {
                                ensureActive()
                                success = runCatching {
                                    OmniInferMnnModelsManager.ensureModelReady(modelId)
                                }.getOrDefault(false)
                            }
                            if (!success) {
                                ensureActive()
                                success = runCatching {
                                    OmniInferLiteRtModelsManager.ensureModelReady(modelId)
                                }.getOrDefault(false)
                            }
                            if (!success) {
                                ensureActive()
                                success = runCatching {
                                    OmniInferQnnModelsManager.ensureModelReady(modelId)
                                }.getOrDefault(false)
                            }
                            if (!success) error = "Model not found or load failed"
                        }
                        ensureActive()
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        OmniLog.i(TAG, "[preloadModel] cancelled modelId=$modelId")
                        mainHandler.post {
                            runCatching {
                                result.success(mapOf(
                                    "success" to false,
                                    "cancelled" to true,
                                    "modelId" to modelId,
                                ))
                            }
                        }
                        return@launch
                    }
                    OmniLog.i(TAG, "[preloadModel] done modelId=$modelId success=$success")
                    mainHandler.post {
                        result.success(mapOf(
                            "success" to success,
                            "modelId" to modelId,
                            "error" to error,
                        ))
                    }
                }
                return
            }

            "refreshLanProxyToken" -> {
                result.success(OmniInferLocalRuntime.refreshLanProxyToken())
                return
            }

            "stopLanProxy" -> {
                OmniInferLocalRuntime.stopLanProxy()
                result.success(getCurrentConfig())
                return
            }
        }

        // The installed list merges all four backends, so a call may target a model that does
        // not belong to the currently selected backend (load / set-active on a cross-backend
        // entry). Such calls pass an explicit "backend"; everything else uses the selection.
        val explicitBackend = call.argument<String>("backend")
            ?.let { OmniInferLocalRuntime.normalizeBackend(it) }
        when (explicitBackend ?: getSelectedBackend()) {
            OmniInferLocalRuntime.BACKEND_OMNIINFER_MNN -> handleMnnCall(call, result)
            OmniInferLocalRuntime.BACKEND_EXECUTORCH_QNN -> handleQnnCall(call, result)
            // litert-npu 与 litert 共用 LiteRT 管理器（runtime 层也是合并处理的）。
            // 原来漏了这个常量，它会落到 else → 被当成 llama.cpp：列 GGUF、下 GGUF，
            // 再把 .gguf 交给 NPU 后端加载。
            OmniInferLocalRuntime.BACKEND_LITERT,
            OmniInferLocalRuntime.BACKEND_LITERT_NPU -> handleLiteRtCall(call, result)
            else -> handleLlamaCppCall(call, result)
        }
    }

    private fun getSelectedBackend(): String {
        val rawBackend = backendMmkv.decodeString(MMKV_BACKEND_KEY, DEFAULT_BACKEND)
        val normalizedBackend = OmniInferLocalRuntime.normalizeBackend(rawBackend)
        if (normalizedBackend != rawBackend) {
            backendMmkv.encode(MMKV_BACKEND_KEY, normalizedBackend)
        }
        OmniInferLocalRuntime.setSelectedBackend(normalizedBackend)
        return normalizedBackend
    }

    private fun getCurrentConfig(): Map<String, Any?> {
        return when (getSelectedBackend()) {
            OmniInferLocalRuntime.BACKEND_OMNIINFER_MNN -> OmniInferMnnModelsManager.getConfig()
            OmniInferLocalRuntime.BACKEND_EXECUTORCH_QNN -> OmniInferQnnModelsManager.getConfig()
            OmniInferLocalRuntime.BACKEND_LITERT,
            OmniInferLocalRuntime.BACKEND_LITERT_NPU -> OmniInferLiteRtModelsManager.getConfig()
            else -> OmniInferModelsManager.getConfig()
        }
    }

    private fun handleLlamaCppCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getOverview" -> runSuspend(result) {
                OmniInferModelsManager.getOverview(
                    installedQuery = call.argument<String>("installedQuery"),
                    marketQuery = call.argument<String>("marketQuery"),
                    marketCategory = call.argument<String>("marketCategory"),
                )
            }

            "listInstalledModels" -> runSuspend(result) {
                OmniInferModelsManager.listInstalledModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                )
            }

            "refreshInstalledModels" -> runSuspend(result) {
                OmniInferModelsManager.refreshInstalledModels()
            }

            "listMarketModels" -> runSuspend(result) {
                OmniInferModelsManager.listMarketModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                    refresh = call.argument<Boolean>("refresh") == true,
                )
            }

            "refreshMarketModels" -> runSuspend(result) {
                OmniInferModelsManager.refreshMarketModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                )
            }

            "getConfig" -> result.success(OmniInferModelsManager.getConfig())

            "saveConfig" -> {
                val args = (call.arguments as? Map<*, *>) ?: emptyMap<String, Any?>()
                // saveConfig 里改端口会连带 stop() 卸载引擎（native free 掉几 GB 模型），
                // stopApiService 已经为此挪到 IO，这条等价路径不能留在主线程。
                runSuspend(result) { OmniInferModelsManager.saveConfig(args) }
            }

            "setActiveModel" -> {
                result.success(OmniInferModelsManager.setActiveModel(call.argument<String>("modelId")))
            }

            "startApiService" -> {
                val modelId = call.argument<String>("modelId")
                // 加载模型 / 起局域网代理可能抛异常（so 缺失、mmap 失败、OOM）。
                // runSuspend 在 IO 上执行，异常也会回成 result.error；原来的裸
                // scope.launch 会让这种异常直接冒到主线程把 App 崩掉。
                runSuspend(result) { OmniInferModelsManager.startApiService(modelId) }
            }

            "stopApiService" -> runSuspend(result) {
                // 卸载引擎可能等正在生成的请求结束，不能放在主线程；
                // runSuspend 在 IO 上执行并把异常回成 result.error。
                OmniInferModelsManager.stopApiService()
            }

            "startDownload" -> {
                val modelId = call.argument<String>("modelId")
                if (modelId.isNullOrBlank()) {
                    result.error(ERROR_CODE, "modelId is required", null)
                } else {
                    // startDownload 会遍历目标目录统计已下载字节 + StatFs + 首次解析
                    // 市场 JSON（读 assets/cache 并反序列化）；pauseDownload 同样要扫盘。
                    // MethodChannel 回调在主线程，与 MNN 分支对齐改到 IO 执行。
                    runSuspend(result) {
                        OmniInferModelsManager.startDownload(modelId)
                        true
                    }
                }
            }

            "pauseDownload" -> {
                val modelId = call.argument<String>("modelId")
                if (modelId.isNullOrBlank()) {
                    result.error(ERROR_CODE, "modelId is required", null)
                } else {
                    runSuspend(result) {
                        OmniInferModelsManager.pauseDownload(modelId)
                        true
                    }
                }
            }

            "deleteModel" -> runSuspend(result) {
                val modelId = call.argument<String>("modelId") ?: error("modelId is required")
                OmniInferModelsManager.deleteModel(modelId)
            }


            else -> result.notImplemented()
        }
    }

    private fun handleMnnCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getOverview" -> runSuspend(result) {
                OmniInferMnnModelsManager.getOverview(
                    installedQuery = call.argument<String>("installedQuery"),
                    marketQuery = call.argument<String>("marketQuery"),
                    marketCategory = call.argument<String>("marketCategory"),
                )
            }

            "listInstalledModels" -> runSuspend(result) {
                OmniInferMnnModelsManager.listInstalledModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                )
            }

            "refreshInstalledModels" -> runSuspend(result) {
                OmniInferMnnModelsManager.refreshInstalledModels()
            }

            "listMarketModels" -> runSuspend(result) {
                OmniInferMnnModelsManager.listMarketModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                    refresh = call.argument<Boolean>("refresh") == true,
                )
            }

            "refreshMarketModels" -> runSuspend(result) {
                OmniInferMnnModelsManager.refreshMarketModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                )
            }

            "getConfig" -> result.success(OmniInferMnnModelsManager.getConfig())

            "saveConfig" -> {
                val args = (call.arguments as? Map<*, *>) ?: emptyMap<String, Any?>()
                // 同上：端口变更会同步卸载引擎，不能跑在主线程。
                runSuspend(result) { OmniInferMnnModelsManager.saveConfig(args) }
            }

            "setActiveModel" -> {
                result.success(OmniInferMnnModelsManager.setActiveModel(call.argument<String>("modelId")))
            }

            "startApiService" -> {
                val modelId = call.argument<String>("modelId")
                // 加载模型 / 起局域网代理可能抛异常（so 缺失、mmap 失败、OOM）。
                // runSuspend 在 IO 上执行，异常也会回成 result.error；原来的裸
                // scope.launch 会让这种异常直接冒到主线程把 App 崩掉。
                runSuspend(result) { OmniInferMnnModelsManager.startApiService(modelId) }
            }

            "stopApiService" -> runSuspend(result) {
                // 卸载引擎可能等正在生成的请求结束，不能放在主线程；
                // runSuspend 在 IO 上执行并把异常回成 result.error。
                OmniInferMnnModelsManager.stopApiService()
            }

            "startDownload" -> {
                val modelId = call.argument<String>("modelId")
                if (modelId.isNullOrBlank()) {
                    result.error(ERROR_CODE, "modelId is required", null)
                } else {
                    // startDownload 会遍历目标目录统计已下载字节 + StatFs + 写 MMKV +
                    // 起前台服务；MethodChannel 回调在主线程，原来直接跑会掉帧甚至 ANR。
                    runSuspend(result) {
                        OmniInferMnnModelsManager.startDownload(modelId)
                        true
                    }
                }
            }

            "pauseDownload" -> {
                val modelId = call.argument<String>("modelId")
                if (modelId.isNullOrBlank()) {
                    result.error(ERROR_CODE, "modelId is required", null)
                } else {
                    runSuspend(result) {
                        OmniInferMnnModelsManager.pauseDownload(modelId)
                        true
                    }
                }
            }

            "deleteModel" -> runSuspend(result) {
                val modelId = call.argument<String>("modelId") ?: error("modelId is required")
                OmniInferMnnModelsManager.deleteModel(modelId)
            }


            else -> result.notImplemented()
        }
    }

    private fun handleQnnCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getOverview" -> runSuspend(result) {
                OmniInferQnnModelsManager.getOverview(
                    installedQuery = call.argument<String>("installedQuery"),
                    marketQuery = call.argument<String>("marketQuery"),
                    marketCategory = call.argument<String>("marketCategory"),
                )
            }

            "listInstalledModels" -> runSuspend(result) {
                OmniInferQnnModelsManager.listInstalledModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                )
            }

            "refreshInstalledModels" -> runSuspend(result) {
                OmniInferQnnModelsManager.refreshInstalledModels()
            }

            "listMarketModels" -> runSuspend(result) {
                OmniInferQnnModelsManager.listMarketModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                    refresh = call.argument<Boolean>("refresh") == true,
                )
            }

            "refreshMarketModels" -> runSuspend(result) {
                OmniInferQnnModelsManager.refreshMarketModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                )
            }

            "getConfig" -> result.success(OmniInferQnnModelsManager.getConfig())

            "saveConfig" -> {
                val args = (call.arguments as? Map<*, *>) ?: emptyMap<String, Any?>()
                // 同上：端口变更会同步卸载引擎，不能跑在主线程。
                runSuspend(result) { OmniInferQnnModelsManager.saveConfig(args) }
            }

            "setActiveModel" -> {
                result.success(OmniInferQnnModelsManager.setActiveModel(call.argument<String>("modelId")))
            }

            "startApiService" -> {
                val modelId = call.argument<String>("modelId")
                // 加载模型 / 起局域网代理可能抛异常（so 缺失、mmap 失败、OOM）。
                // runSuspend 在 IO 上执行，异常也会回成 result.error；原来的裸
                // scope.launch 会让这种异常直接冒到主线程把 App 崩掉。
                runSuspend(result) { OmniInferQnnModelsManager.startApiService(modelId) }
            }

            "stopApiService" -> runSuspend(result) {
                // 卸载引擎可能等正在生成的请求结束，不能放在主线程；
                // runSuspend 在 IO 上执行并把异常回成 result.error。
                OmniInferQnnModelsManager.stopApiService()
            }

            "startDownload" -> {
                val modelId = call.argument<String>("modelId")
                if (modelId.isNullOrBlank()) {
                    result.error(ERROR_CODE, "modelId is required", null)
                } else {
                    // 同 llama.cpp 分支：起/停下载都要扫盘 + 解析市场 JSON，不能占主线程。
                    runSuspend(result) {
                        OmniInferQnnModelsManager.startDownload(modelId)
                        true
                    }
                }
            }

            "pauseDownload" -> {
                val modelId = call.argument<String>("modelId")
                if (modelId.isNullOrBlank()) {
                    result.error(ERROR_CODE, "modelId is required", null)
                } else {
                    runSuspend(result) {
                        OmniInferQnnModelsManager.pauseDownload(modelId)
                        true
                    }
                }
            }

            "deleteModel" -> runSuspend(result) {
                val modelId = call.argument<String>("modelId") ?: error("modelId is required")
                OmniInferQnnModelsManager.deleteModel(modelId)
            }

            else -> result.notImplemented()
        }
    }

    private fun handleLiteRtCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getOverview" -> runSuspend(result) {
                OmniInferLiteRtModelsManager.getOverview(
                    installedQuery = call.argument<String>("installedQuery"),
                    marketQuery = call.argument<String>("marketQuery"),
                    marketCategory = call.argument<String>("marketCategory"),
                )
            }

            "listInstalledModels" -> runSuspend(result) {
                OmniInferLiteRtModelsManager.listInstalledModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                )
            }

            "refreshInstalledModels" -> runSuspend(result) {
                OmniInferLiteRtModelsManager.refreshInstalledModels()
            }

            "listMarketModels" -> runSuspend(result) {
                OmniInferLiteRtModelsManager.listMarketModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                    refresh = call.argument<Boolean>("refresh") == true,
                )
            }

            "refreshMarketModels" -> runSuspend(result) {
                OmniInferLiteRtModelsManager.refreshMarketModels(
                    query = call.argument<String>("query"),
                    category = call.argument<String>("category"),
                )
            }

            "getConfig" -> result.success(OmniInferLiteRtModelsManager.getConfig())

            "saveConfig" -> {
                val args = (call.arguments as? Map<*, *>) ?: emptyMap<String, Any?>()
                // 同上：端口变更会同步卸载引擎，不能跑在主线程。
                runSuspend(result) { OmniInferLiteRtModelsManager.saveConfig(args) }
            }

            "setActiveModel" -> {
                result.success(OmniInferLiteRtModelsManager.setActiveModel(call.argument<String>("modelId")))
            }

            "startApiService" -> {
                val modelId = call.argument<String>("modelId")
                // 加载模型 / 起局域网代理可能抛异常（so 缺失、mmap 失败、OOM）。
                // runSuspend 在 IO 上执行，异常也会回成 result.error；原来的裸
                // scope.launch 会让这种异常直接冒到主线程把 App 崩掉。
                runSuspend(result) { OmniInferLiteRtModelsManager.startApiService(modelId) }
            }

            "stopApiService" -> runSuspend(result) {
                // 卸载引擎可能等正在生成的请求结束，不能放在主线程；
                // runSuspend 在 IO 上执行并把异常回成 result.error。
                OmniInferLiteRtModelsManager.stopApiService()
            }

            "startDownload" -> {
                val modelId = call.argument<String>("modelId")
                if (modelId.isNullOrBlank()) {
                    result.error(ERROR_CODE, "modelId is required", null)
                } else {
                    // 同 llama.cpp 分支：起/停下载都要扫盘 + 解析市场 JSON，不能占主线程。
                    runSuspend(result) {
                        OmniInferLiteRtModelsManager.startDownload(modelId)
                        true
                    }
                }
            }

            "pauseDownload" -> {
                val modelId = call.argument<String>("modelId")
                if (modelId.isNullOrBlank()) {
                    result.error(ERROR_CODE, "modelId is required", null)
                } else {
                    runSuspend(result) {
                        OmniInferLiteRtModelsManager.pauseDownload(modelId)
                        true
                    }
                }
            }

            "deleteModel" -> runSuspend(result) {
                val modelId = call.argument<String>("modelId") ?: error("modelId is required")
                OmniInferLiteRtModelsManager.deleteModel(modelId)
            }

            else -> result.notImplemented()
        }
    }

    private fun runSuspend(
        result: MethodChannel.Result,
        block: suspend () -> Any?,
    ) {
        // block 里基本都是文件系统 / 网络操作（扫描四个后端目录、刷新市场、删除模型），
        // 不能在主线程跑：scope 是 Main.immediate，原来直接 launch 会让「打开本地模型页」
        // 在主线程上列目录、读文件属性。改成 IO 执行，回调再切回主线程给 Flutter。
        scope.launch(Dispatchers.IO) {
            try {
                val value = block()
                withContext(Dispatchers.Main) { result.success(value) }
            } catch (error: Throwable) {
                // 取消是正常收摊，不能当成业务错误回给 Dart（也会打破 StructuredConcurrency）。
                if (error is CancellationException) throw error
                withContext(Dispatchers.Main) {
                    result.error(
                        ERROR_CODE,
                        error.message ?: "unknown_error",
                        null,
                    )
                }
            }
        }
    }
}
