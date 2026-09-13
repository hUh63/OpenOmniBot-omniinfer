package cn.com.omnimind.bot.ui.channel

import android.content.Context
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.im.ImChannelManager
import cn.com.omnimind.bot.im.ImChannelStore
import cn.com.omnimind.bot.im.ImChannelType
import cn.com.omnimind.bot.im.TelegramImConfig
import cn.com.omnimind.bot.im.WechatImConfig
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImChannel {
    private val channelName = "cn.com.omnimind.bot/ImChannel"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var channel: MethodChannel? = null
    private var appContext: Context? = null

    fun onCreate(context: Context) {
        appContext = context.applicationContext
    }

    fun setChannel(flutterEngine: FlutterEngine) {
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
        channel?.setMethodCallHandler { call, result ->
            val context = appContext
            if (context == null) {
                result.error("IM_INIT_ERROR", "Context not initialized", null)
                return@setMethodCallHandler
            }
            scope.launch {
                try {
                    val response = when (call.method) {
                        "state" -> ImChannelManager.currentState(context)
                        "refresh" -> ImChannelManager.reload(context)
                        "saveTelegram" -> {
                            val existing = ImChannelStore(context).loadSettings().telegram
                            ImChannelManager.saveTelegram(
                                context,
                                parseTelegramConfig(call.arguments, existing)
                            )
                        }

                        "saveWechat" -> {
                            val existing = ImChannelStore(context).loadSettings().wechat
                            ImChannelManager.saveWechat(
                                context,
                                parseWechatConfig(call.arguments, existing)
                            )
                        }

                        "setChannelEnabled" -> {
                            val args = normalizeMap(call.arguments)
                            val type = ImChannelType.fromId(args["channel"]?.toString())
                                ?: throw IllegalArgumentException("Unknown IM channel")
                            val enabled = readBoolean(args["enabled"]) ?: false
                            ImChannelManager.setChannelEnabled(context, type, enabled)
                        }

                        "requestWechatQr" -> ImChannelManager.requestWechatQr(context)
                        "clearPeerSessions" -> ImChannelManager.clearPeerSessions(context)
                        else -> {
                            withContext(Dispatchers.Main) { result.notImplemented() }
                            return@launch
                        }
                    }
                    respondSuccess(result, response)
                } catch (error: Throwable) {
                    OmniLog.e("[ImChannel]", "channel error: ${error.message}")
                    withContext(Dispatchers.Main) {
                        result.error("IM_ERROR", error.message ?: error.javaClass.simpleName, null)
                    }
                }
            }
        }
    }

    fun clear() {
        channel?.setMethodCallHandler(null)
        channel = null
    }

    private fun parseTelegramConfig(
        raw: Any?,
        existing: TelegramImConfig
    ): TelegramImConfig {
        val args = normalizeMap(raw)
        return TelegramImConfig(
            enabled = readBoolean(args["enabled"]) ?: existing.enabled,
            botToken = readString(args["botToken"]) ?: existing.botToken,
            apiBaseUrl = readString(args["apiBaseUrl"]) ?: existing.apiBaseUrl,
            allowedChatIds = parsePeerList(readString(args["allowedChatIds"]) ?: ""),
            chunkSize = readInt(args["chunkSize"]) ?: existing.chunkSize,
            dropPendingUpdates = readBoolean(args["dropPendingUpdates"])
                ?: existing.dropPendingUpdates
        )
    }

    private fun parseWechatConfig(
        raw: Any?,
        existing: WechatImConfig
    ): WechatImConfig {
        val args = normalizeMap(raw)
        return WechatImConfig(
            enabled = readBoolean(args["enabled"]) ?: existing.enabled,
            token = readString(args["token"]) ?: existing.token,
            baseUrl = readString(args["baseUrl"]) ?: existing.baseUrl,
            botType = readString(args["botType"]) ?: existing.botType,
            version = readString(args["version"]) ?: existing.version,
            chunkSize = readInt(args["chunkSize"]) ?: existing.chunkSize
        )
    }

    private fun normalizeMap(raw: Any?): Map<String, Any?> {
        return (raw as? Map<*, *>)?.entries?.associate { entry ->
            entry.key.toString() to entry.value
        } ?: emptyMap()
    }

    private fun readString(value: Any?): String? {
        return value?.toString()
    }

    private fun readBoolean(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> null
        }
    }

    private fun readInt(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun parsePeerList(value: String): Set<String> {
        return value.split(',', '\n', ';', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private suspend fun respondSuccess(result: MethodChannel.Result, value: Any?) {
        withContext(Dispatchers.Main) {
            result.success(value)
        }
    }
}
