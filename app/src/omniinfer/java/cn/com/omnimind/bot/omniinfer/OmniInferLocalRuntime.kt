package cn.com.omnimind.bot.omniinfer

import android.content.Context
import cn.com.omnimind.baselib.llm.MnnLocalProviderStateStore
import cn.com.omnimind.baselib.util.OmniLog
import com.omniinfer.server.OmniInferServer
import com.tencent.mmkv.MMKV
import java.util.Locale

object OmniInferLocalRuntime {
    private const val TAG = "OmniInferLocalRuntime"
    const val BACKEND_LLAMA_CPP = "llama.cpp"
    const val BACKEND_OMNIINFER_MNN = "omniinfer-mnn"
    const val BACKEND_EXECUTORCH_QNN = "executorch-qnn"
    const val BACKEND_LITERT = "litert"
    const val BACKEND_LITERT_NPU = "litert-npu"

    private const val MMKV_ID = "omniinfer_config"
    private const val KEY_API_PORT = "apiPort"
    private const val KEY_API_TOKEN = "apiToken"
    private const val KEY_AUTH_ENABLED = "authEnabled"
    private const val LEGACY_LAN_TOKEN_KEY = "omniinfer_lan_proxy_token"
    private const val KEY_SELECTED_BACKEND = "omniinfer_selected_backend"
    private const val KEY_LOADED_BACKEND = "omniinfer_loaded_backend"
    private const val KEY_LOADED_MODEL_ID = "omniinfer_loaded_model_id"
    private const val DEFAULT_PORT = 9099

    private var appContext: Context? = null
    private val mmkv: MMKV by lazy { MMKV.mmkvWithID(MMKV_ID) }

    fun setContext(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        applyApiAuth()
        syncProviderState()
    }

    fun getContext(): Context {
        return appContext ?: error("OmniInfer runtime context is not initialized")
    }

    fun normalizeBackend(rawBackend: String?): String {
        // Keep this table aligned with ui/lib/services/inference_backend.dart.
        // Anything both layers must agree on belongs in exactly one of the two
        // (Kotlin + Dart), never in a third copy.
        return when (rawBackend?.trim()?.lowercase(Locale.US)) {
            BACKEND_OMNIINFER_MNN, "mnn", "mnn-cpu" -> BACKEND_OMNIINFER_MNN
            BACKEND_EXECUTORCH_QNN, "qnn" -> BACKEND_EXECUTORCH_QNN
            BACKEND_LITERT_NPU, "litert-lm-npu", "litertlm-npu", "litert/npu" ->
                BACKEND_LITERT_NPU
            BACKEND_LITERT, "litert-lm", "litertlm", "litert/cpu", "litert-lm-cpu" ->
                BACKEND_LITERT
            else -> BACKEND_LLAMA_CPP
        }
    }

    fun getSelectedBackend(): String {
        val stored = mmkv.decodeString(KEY_SELECTED_BACKEND, BACKEND_LLAMA_CPP)
        val normalized = normalizeBackend(stored)
        if (normalized != stored) {
            mmkv.encode(KEY_SELECTED_BACKEND, normalized)
        }
        return normalized
    }

    fun setSelectedBackend(rawBackend: String) {
        mmkv.encode(KEY_SELECTED_BACKEND, normalizeBackend(rawBackend))
    }

    fun getPort(): Int {
        val port = mmkv.decodeInt(KEY_API_PORT, DEFAULT_PORT)
        return if (port > 0) port else DEFAULT_PORT
    }

    fun setPort(port: Int) {
        if (port > 0) {
            mmkv.encode(KEY_API_PORT, port)
            syncProviderState()
        }
    }

    /** API key guarding the local server (and the LAN proxy). Blank means "no key". */
    fun getApiToken(): String = mmkv.decodeString(KEY_API_TOKEN, "").orEmpty().trim()

    fun setApiToken(value: String) {
        mmkv.encode(KEY_API_TOKEN, value.trim())
        applyApiAuth()
        syncProviderState()
    }

    /** Whether the `v1` endpoints require the API key. */
    fun isAuthEnabled(): Boolean = mmkv.decodeBool(KEY_AUTH_ENABLED, false)

    fun setAuthEnabled(enabled: Boolean) {
        // Turning the guard on without a token would silently disable it; mint one instead.
        if (enabled && getApiToken().isEmpty()) {
            mmkv.encode(KEY_API_TOKEN, generateApiToken())
        }
        mmkv.encode(KEY_AUTH_ENABLED, enabled)
        applyApiAuth()
        syncProviderState()
    }

    /** UI snapshot of the API-key settings (used by the local-model page). */
    fun apiAuthState(): Map<String, Any?> = mapOf(
        "authEnabled" to isAuthEnabled(),
        "apiToken" to getApiToken(),
        "apiPort" to getPort(),
        "baseUrl" to getBaseUrl(),
        "lanToken" to ensureApiToken(),
    )

    fun saveApiAuth(args: Map<*, *>): Map<String, Any?> {
        args["authEnabled"]?.let { setAuthEnabled(it == true) }
        args["apiToken"]?.let { setApiToken(it.toString()) }
        if (args["refreshApiToken"] == true) refreshApiToken()
        return apiAuthState()
    }

    /** Generates (or regenerates) the shared API token. */
    fun refreshApiToken(): String {
        val token = generateApiToken()
        mmkv.encode(KEY_API_TOKEN, token)
        applyApiAuth()
        syncProviderState()
        return token
    }

    /** Shared token, created on demand; adopts the legacy LAN-proxy token if one exists. */
    fun ensureApiToken(): String {
        val existing = getApiToken()
        if (existing.isNotEmpty()) return existing
        val legacy = mmkv.decodeString(LEGACY_LAN_TOKEN_KEY, "").orEmpty().trim()
        val token = legacy.ifEmpty { generateApiToken() }
        mmkv.encode(KEY_API_TOKEN, token)
        return token
    }

    /** Pushes the current setting into the engine (safe to call before the server starts). */
    fun applyApiAuth() {
        OmniInferServer.configureApiAuth(isAuthEnabled(), getApiToken())
    }

    private fun generateApiToken(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
        )
    }

    fun setLanProxyPort(port: Int): Map<String, Any?> {
        val context = appContext ?: return getLanProxyState()
        return OmniInferLanProxyManager.setPort(context, port, getPort()).toMap()
    }

    fun getHost(): String = "127.0.0.1"

    fun getBaseUrl(): String = "http://${getHost()}:${getPort()}"

    fun getLanProxyState(): Map<String, Any?> {
        return OmniInferLanProxyManager.currentState(getPort()).toMap()
    }

    fun isReady(): Boolean {
        syncProviderState()
        return OmniInferServer.isReady()
    }

    fun getLoadedBackend(): String {
        if (!OmniInferServer.isReady()) {
            clearLoadedState()
        }
        return mmkv.decodeString(KEY_LOADED_BACKEND, "").orEmpty()
    }

    fun getLoadedModelId(): String {
        if (!OmniInferServer.isReady()) {
            clearLoadedState()
        }
        return mmkv.decodeString(KEY_LOADED_MODEL_ID, "").orEmpty()
    }

    fun isModelLoaded(backend: String, modelId: String): Boolean {
        val normalizedModelId = modelId.trim()
        if (normalizedModelId.isEmpty()) {
            return false
        }
        return isReady() &&
            getLoadedBackend() == normalizeBackend(backend) &&
            getLoadedModelId() == normalizedModelId
    }

    fun loadModel(
        modelId: String,
        modelPath: String,
        backend: String,
        extraConfig: Map<String, String>? = null,
        nCtx: Int = 16384,
    ): Boolean {
        val normalizedModelId = modelId.trim()
        if (normalizedModelId.isEmpty() || modelPath.isBlank()) {
            OmniLog.w(TAG, "[loadModel] invalid params: modelId='$modelId', modelPath='$modelPath'")
            return false
        }
        val normalizedBackend = normalizeBackend(backend)
        val serverBackend = when (normalizedBackend) {
            BACKEND_OMNIINFER_MNN -> "mnn"
            BACKEND_EXECUTORCH_QNN -> "executorch-qnn"
            BACKEND_LITERT -> "litert"
            BACKEND_LITERT_NPU -> "litert-npu"
            else -> BACKEND_LLAMA_CPP
        }
        val port = getPort()
        applyApiAuth()
        OmniLog.i(
            TAG,
            "[loadModel] >> OmniInferServer.loadModel(" +
                "modelId=$normalizedModelId, modelPath=$modelPath, " +
                "backend=$serverBackend, port=$port, nCtx=$nCtx, extraConfig=$extraConfig)"
        )
        val success = OmniInferServer.loadModel(
            modelPath = modelPath,
            backend = serverBackend,
            port = port,
            nCtx = nCtx,
            extraConfig = extraConfig,
        )
        OmniLog.i(TAG, "[loadModel] << OmniInferServer.loadModel result=$success")
        if (success) {
            mmkv.encode(KEY_LOADED_BACKEND, normalizedBackend)
            mmkv.encode(KEY_LOADED_MODEL_ID, normalizedModelId)
        } else {
            OmniInferServer.stop()
            clearLoadedState()
        }
        syncProviderState()
        return success
    }

    fun stop() {
        OmniInferLanProxyManager.stop()
        OmniInferServer.stop()
        clearLoadedState()
        syncProviderState()
    }

    fun startLanProxy(): Boolean {
        val context = appContext ?: return false
        return runCatching {
            OmniInferLanProxyManager.start(context, getPort())
            true
        }.getOrDefault(false)
    }

    fun stopLanProxy() {
        OmniInferLanProxyManager.stop()
    }

    fun refreshLanProxyToken(): Map<String, Any?> {
        val context = appContext ?: return getLanProxyState()
        refreshApiToken()
        return OmniInferLanProxyManager.rotateToken(context, getPort()).toMap()
    }

    fun handleAppOpen(context: Context) {
        setContext(context)
        when (getSelectedBackend()) {
            BACKEND_OMNIINFER_MNN -> OmniInferMnnModelsManager.handleAppOpen()
            BACKEND_EXECUTORCH_QNN -> OmniInferQnnModelsManager.handleAppOpen()
            BACKEND_LITERT, BACKEND_LITERT_NPU -> OmniInferLiteRtModelsManager.handleAppOpen()
            else -> OmniInferModelsManager.handleAppOpen()
        }
    }

    fun listBuiltinProviderModels(): List<Map<String, Any?>> {
        val combined = LinkedHashMap<String, Map<String, Any?>>()
        (OmniInferModelsManager.listInstalledModels() +
            OmniInferMnnModelsManager.listInstalledModels() +
            OmniInferQnnModelsManager.listInstalledModels() +
            OmniInferLiteRtModelsManager.listInstalledModels())
            .forEach { model ->
                val modelId = model["id"]?.toString()?.trim().orEmpty()
                if (modelId.isNotEmpty()) {
                    combined.putIfAbsent(modelId, model)
                }
            }
        return combined.values.toList()
    }

    private fun syncProviderState() {
        val ready = OmniInferServer.isReady()
        if (!ready) {
            clearLoadedState()
        }
        MnnLocalProviderStateStore.update(
            port = getPort(),
            apiKey = if (isAuthEnabled()) getApiToken() else "",
            ready = ready,
        )
    }

    private fun clearLoadedState() {
        mmkv.encode(KEY_LOADED_BACKEND, "")
        mmkv.encode(KEY_LOADED_MODEL_ID, "")
    }
}
