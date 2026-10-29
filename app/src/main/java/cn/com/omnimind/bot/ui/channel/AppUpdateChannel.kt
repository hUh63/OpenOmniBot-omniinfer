package cn.com.omnimind.bot.ui.channel

import android.content.Context
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.update.AppUpdateManager
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUpdateChannel {
    private val channelName = "cn.com.omnimind.bot/app_update"
    private var context: Context? = null
    private var channel: MethodChannel? = null

    // 下载可能在用户退出页面后继续跑几分钟，用独立 scope；不绑定 Activity 生命周期。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onCreate(context: Context) {
        // 只存 Application：FlutterEngine 常驻（App.cachedMainEngine），存 Activity 会让
        // 已销毁的 MainActivity 及其 View 树被闭包链一直引用。
        this.context = context.applicationContext
    }

    /** 轻查询也走 IO：SharedPreferences 首次读盘与 PackageManager IPC 都是同步阻塞调用。 */
    private fun runOnIo(result: MethodChannel.Result, block: () -> Any?) {
        scope.launch {
            val outcome = runCatching(block)
            withContext(Dispatchers.Main) {
                outcome
                    .onSuccess { result.success(it) }
                    .onFailure { error ->
                        OmniLog.e("AppUpdateChannel", "App update query failed", error)
                        result.error(
                            "APP_UPDATE_FAILED",
                            error.message ?: error.javaClass.simpleName,
                            null
                        )
                    }
            }
        }
    }

    fun setChannel(flutterEngine: FlutterEngine) {
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
        channel?.setMethodCallHandler { call, result ->
            handleMethodCall(call, result)
        }
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val safeContext = context
        if (safeContext == null) {
            result.error("CONTEXT_ERROR", "Context not initialized", null)
            return
        }

        when (call.method) {
            "getBetaOptIn" -> runOnIo(result) {
                AppUpdateManager.isBetaOptIn(safeContext)
            }

            "getApkDownloadSource" -> runOnIo(result) {
                AppUpdateManager.getApkDownloadSource(safeContext).value
            }

            "setBetaOptIn" -> {
                val enabled = call.argument<Boolean>("enabled") == true
                runOnIo(result) { AppUpdateManager.setBetaOptIn(safeContext, enabled) }
            }

            "setApkDownloadSource" -> {
                val source = call.argument<String>("source")
                runOnIo(result) {
                    AppUpdateManager.setApkDownloadSource(safeContext, source).value
                }
            }

            "getCachedStatus" -> runOnIo(result) {
                AppUpdateManager.getCachedStatus(safeContext).toMap()
            }

            "checkNow" -> {
                val force = call.argument<Boolean>("force") == true
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        AppUpdateManager.checkNow(safeContext, force = force).toMap()
                    }.onSuccess { payload ->
                        withContext(Dispatchers.Main) {
                            result.success(payload)
                        }
                    }.onFailure {
                        OmniLog.e("AppUpdateChannel", "App update check failed", it)
                        withContext(Dispatchers.Main) {
                            result.error("CHECK_FAILED", it.message ?: "Failed to check updates", null)
                        }
                    }
                }
            }

            "installLatestApk" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        AppUpdateManager.installLatestApk(safeContext)
                    }.onSuccess { installResult ->
                        withContext(Dispatchers.Main) {
                            result.success(
                                mapOf(
                                    "success" to installResult.success,
                                    "status" to installResult.status,
                                    "message" to installResult.message,
                                    "filePath" to installResult.filePath
                                )
                            )
                        }
                    }.onFailure {
                        OmniLog.e("AppUpdateChannel", "Install latest apk failed", it)
                        withContext(Dispatchers.Main) {
                            result.error(
                                "INSTALL_FAILED",
                                it.message ?: "Failed to install latest apk",
                                null
                            )
                        }
                    }
                }
            }

            else -> result.notImplemented()
        }
    }

    fun clear() {
        channel?.setMethodCallHandler(null)
        channel = null
    }
}
