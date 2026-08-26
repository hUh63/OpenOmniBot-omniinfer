package cn.com.omnimind.bot.im

import cn.com.omnimind.baselib.util.OmniLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

internal class OpenILinkWechatConnector(
    private val onCredentialRefresh: (token: String, baseUrl: String?, botId: String?) -> Unit
) : ImConnector {
    override val channel: ImChannelType = ImChannelType.WECHAT

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var status = ImConnectorStatus(channel = channel, sdkAvailable = isSdkAvailable())

    @Volatile
    private var config = WechatImConfig()

    @Volatile
    private var clientRef: Any? = null

    @Volatile
    private var messageCallback: (suspend (ImInboundMessage) -> Unit)? = null

    private var monitorJob: Job? = null
    private var qrLoginJob: Job? = null
    private var stopFlag: AtomicBoolean? = null

    override fun currentStatus(): ImConnectorStatus {
        return status.copy(sdkAvailable = isSdkAvailable())
    }

    override suspend fun start(
        settings: ImChannelSettings,
        onMessage: suspend (ImInboundMessage) -> Unit
    ) {
        stopMonitorOnly()
        config = settings.wechat.normalized()
        messageCallback = onMessage
        val sdkAvailable = isSdkAvailable()
        if (!config.enabled) {
            status = ImConnectorStatus(
                channel = channel,
                enabled = false,
                sdkAvailable = sdkAvailable
            )
            return
        }
        if (!sdkAvailable) {
            status = ImConnectorStatus(
                channel = channel,
                enabled = true,
                sdkAvailable = false,
                lastError = "OpeniLink SDK 未打包"
            )
            return
        }
        if (config.token.isBlank()) {
            status = ImConnectorStatus(
                channel = channel,
                enabled = true,
                sdkAvailable = true,
                lastError = "等待扫码或 Token"
            )
            return
        }
        try {
            val client = buildClient(config)
            clientRef = client
            startMonitor(client, onMessage)
        } catch (error: Throwable) {
            status = ImConnectorStatus(
                channel = channel,
                enabled = true,
                sdkAvailable = true,
                lastError = rootMessage(error)
            )
        }
    }

    override suspend fun stop() {
        qrLoginJob?.cancel()
        qrLoginJob = null
        stopMonitorOnly()
        status = status.copy(
            running = false,
            connected = false,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun sendText(peerId: String, text: String) {
        val client = clientRef ?: buildClient(config.normalized()).also { clientRef = it }
        try {
            client.javaClass.getMethod("push", String::class.java, String::class.java)
                .invoke(client, peerId, text)
        } catch (error: InvocationTargetException) {
            throw IllegalStateException(rootMessage(error))
        }
    }

    override suspend fun requestQr(): Map<String, Any?> {
        if (!isSdkAvailable()) {
            status = status.copy(
                sdkAvailable = false,
                lastError = "OpeniLink SDK 未打包",
                updatedAt = System.currentTimeMillis()
            )
            return mapOf(
                "ok" to false,
                "channel" to channel.id,
                "error" to "OpeniLink SDK 未打包"
            )
        }
        val activeConfig = config.normalized()
        val qrDeferred = CompletableDeferred<Map<String, Any?>>()
        qrLoginJob?.cancel()
        qrLoginJob = scope.launch {
            try {
                val client = buildClient(activeConfig)
                clientRef = client
                val callbacksClass = Class.forName("com.openilink.auth.LoginCallbacks")
                val callbacks = Proxy.newProxyInstance(
                    callbacksClass.classLoader,
                    arrayOf(callbacksClass)
                ) { _, method, args ->
                    when (method.name) {
                        "onQRCode" -> {
                            val qrContent = args?.firstOrNull()?.toString().orEmpty()
                            status = status.copy(
                                enabled = activeConfig.enabled,
                                sdkAvailable = true,
                                lastError = "",
                                updatedAt = System.currentTimeMillis()
                            )
                            if (!qrDeferred.isCompleted) {
                                qrDeferred.complete(
                                    mapOf(
                                        "ok" to true,
                                        "channel" to channel.id,
                                        "qrContent" to qrContent,
                                        "message" to "请使用微信扫码确认"
                                    )
                                )
                            }
                        }

                        "onScanned" -> {
                            status = status.copy(
                                enabled = activeConfig.enabled,
                                sdkAvailable = true,
                                lastError = "已扫码，请在微信确认",
                                updatedAt = System.currentTimeMillis()
                            )
                        }

                        "onExpired" -> {
                            val attempt = args?.getOrNull(0)?.toString().orEmpty()
                            val max = args?.getOrNull(1)?.toString().orEmpty()
                            status = status.copy(
                                enabled = activeConfig.enabled,
                                sdkAvailable = true,
                                lastError = "二维码已刷新 $attempt/$max",
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                    }
                    null
                }
                val result = client.javaClass.getMethod("loginWithQR", callbacksClass)
                    .invoke(client, callbacks)
                val connected = invokeBoolean(result, "isConnected")
                if (connected) {
                    val token = invokeString(result, "getBotToken")
                        ?: invokeString(client, "getToken")
                        ?: ""
                    val baseUrl = invokeString(result, "getBaseUrl")
                        ?: invokeString(client, "getBaseUrl")
                    val botId = invokeString(result, "getBotId")
                    if (token.isNotBlank()) {
                        onCredentialRefresh(token, baseUrl, botId)
                        config = config.copy(
                            enabled = true,
                            token = token,
                            baseUrl = baseUrl ?: config.baseUrl
                        ).normalized()
                    }
                    status = status.copy(
                        enabled = config.enabled,
                        connected = true,
                        accountLabel = botId.orEmpty(),
                        sdkAvailable = true,
                        lastError = "",
                        updatedAt = System.currentTimeMillis()
                    )
                    val callback = messageCallback
                    if (config.enabled && callback != null) {
                        startMonitor(client, callback)
                    }
                } else {
                    val message = invokeString(result, "getMessage") ?: "扫码登录失败"
                    status = status.copy(
                        enabled = activeConfig.enabled,
                        sdkAvailable = true,
                        connected = false,
                        lastError = message,
                        updatedAt = System.currentTimeMillis()
                    )
                    if (!qrDeferred.isCompleted) {
                        qrDeferred.complete(
                            mapOf(
                                "ok" to false,
                                "channel" to channel.id,
                                "error" to message
                            )
                        )
                    }
                }
            } catch (error: Throwable) {
                val message = rootMessage(error)
                status = status.copy(
                    sdkAvailable = true,
                    connected = false,
                    lastError = message,
                    updatedAt = System.currentTimeMillis()
                )
                if (!qrDeferred.isCompleted) {
                    qrDeferred.complete(
                        mapOf(
                            "ok" to false,
                            "channel" to channel.id,
                            "error" to message
                        )
                    )
                }
            }
        }
        return withTimeoutOrNull(15_000) { qrDeferred.await() }
            ?: mapOf(
                "ok" to false,
                "channel" to channel.id,
                "error" to "获取二维码超时"
            )
    }

    private fun startMonitor(
        client: Any,
        onMessage: suspend (ImInboundMessage) -> Unit
    ) {
        stopMonitorOnly()
        val flag = AtomicBoolean(false)
        stopFlag = flag
        status = status.copy(
            enabled = true,
            running = true,
            connected = true,
            sdkAvailable = true,
            lastError = "",
            updatedAt = System.currentTimeMillis()
        )
        monitorJob = scope.launch {
            try {
                val handlerClass = Class.forName("com.openilink.monitor.MessageHandler")
                val optionsClass = Class.forName("com.openilink.monitor.MonitorOptions")
                val messageClass = Class.forName("com.openilink.model.WeixinMessage")
                val helperClass = Class.forName("com.openilink.util.MessageHelper")
                val extractText = helperClass.getMethod("extractText", messageClass)
                val handler = Proxy.newProxyInstance(
                    handlerClass.classLoader,
                    arrayOf(handlerClass)
                ) { _, method, args ->
                    if (method.name == "handle") {
                        val msg = args?.firstOrNull()
                        if (msg != null) {
                            val text = extractText.invoke(null, msg)?.toString().orEmpty().trim()
                            val peerId = invokeString(msg, "getFromUserId").orEmpty()
                            if (text.isNotEmpty() && peerId.isNotEmpty()) {
                                scope.launch {
                                    onMessage(
                                        ImInboundMessage(
                                            channel = channel,
                                            peerId = peerId,
                                            peerDisplayName = peerId,
                                            text = text,
                                            messageId = invokeString(msg, "getMessageId").orEmpty(),
                                            timestamp = invokeLong(msg, "getCreateTimeMs")
                                                ?: System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                    }
                    null
                }
                client.javaClass.getMethod(
                    "monitor",
                    handlerClass,
                    optionsClass,
                    AtomicBoolean::class.java
                ).invoke(client, handler, null, flag)
            } catch (error: Throwable) {
                if (!flag.get()) {
                    val message = rootMessage(error)
                    OmniLog.e(TAG, "monitor error: $message")
                    status = status.copy(
                        running = false,
                        connected = false,
                        sdkAvailable = true,
                        lastError = message,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private fun stopMonitorOnly() {
        stopFlag?.set(true)
        stopFlag = null
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun buildClient(activeConfig: WechatImConfig): Any {
        val clientClass = Class.forName("com.openilink.ILinkClient")
        val builder = requireNotNull(clientClass.getMethod("builder").invoke(null)) {
            "OpeniLink builder unavailable"
        }
        invokeBuilderMethod(builder, "token", activeConfig.token)
        invokeBuilderMethod(builder, "baseUrl", activeConfig.baseUrl)
        invokeBuilderMethod(builder, "botType", activeConfig.botType)
        invokeBuilderMethod(builder, "version", activeConfig.version)
        return requireNotNull(builder.javaClass.getMethod("build").invoke(builder)) {
            "OpeniLink client unavailable"
        }
    }

    private fun invokeBuilderMethod(builder: Any, name: String, value: String) {
        builder.javaClass.getMethod(name, String::class.java).invoke(builder, value)
    }

    private fun invokeString(target: Any?, method: String): String? {
        if (target == null) return null
        return runCatching {
            target.javaClass.getMethod(method).invoke(target)?.toString()
        }.getOrNull()
    }

    private fun invokeBoolean(target: Any?, method: String): Boolean {
        if (target == null) return false
        return runCatching {
            target.javaClass.getMethod(method).invoke(target) == true
        }.getOrDefault(false)
    }

    private fun invokeLong(target: Any?, method: String): Long? {
        if (target == null) return null
        return runCatching {
            when (val value = target.javaClass.getMethod(method).invoke(target)) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
        }.getOrNull()
    }

    private fun isSdkAvailable(): Boolean {
        return runCatching {
            Class.forName("com.openilink.ILinkClient")
            true
        }.getOrDefault(false)
    }

    private fun rootMessage(error: Throwable): String {
        val root = if (error is InvocationTargetException && error.targetException != null) {
            error.targetException
        } else {
            error
        }
        return root.message ?: root.javaClass.simpleName
    }

    companion object {
        private const val TAG = "[OpenILinkWechatConnector]"
    }
}
