package cn.com.omnimind.bot.ui.channel

import cn.com.omnimind.bot.util.AssistsUtil
import cn.com.omnimind.uikit.loader.FloatingHalfScreenLoader
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenDialogChannel {
    private val eventChannel = "cn.com.omnimind.bot/ScreenDialogEvent"
    private var channel: MethodChannel? = null
    // 这个 scope 会在 clear() 里被 cancel —— 必须能在下次 setChannel 时重建，
    // 否则 Flutter view 重建之后所有对话框调用都落在一个已取消的 scope 上：
    // 不回包、也不报错，Dart 侧永久 loading。
    private var mainJob: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun setChannel(flutterEngine: FlutterEngine) {
        if (!mainJob.isActive) {
            mainJob = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, eventChannel)
        channel?.setMethodCallHandler { call, rawResult ->
            val result = OnceResult(rawResult, "[ScreenDialogChannel]")
            when (call.method) {
                "closeDialog" -> {
                    mainJob.launch {
                        withContext(Dispatchers.Main) { result.success("Success") }
                        AssistsUtil.UI.closeScreenDialog()
                    }
                }

                "closeChatBotDialog" -> {
                    mainJob.launch {
                        withContext(Dispatchers.Main) { result.success("Success") }
                        AssistsUtil.UI.closeChatBotDialog()
                    }
                }

                "hideForExternalActivity" -> {
                    mainJob.launch {
                        val hidden = withContext(Dispatchers.Main) {
                            FloatingHalfScreenLoader.hideForExternalActivity()
                        }
                        result.success(hidden)
                    }
                }

                "restoreAfterExternalActivity" -> {
                    mainJob.launch {
                        val restored = withContext(Dispatchers.Main) {
                            FloatingHalfScreenLoader.restoreAfterExternalActivity()
                        }
                        result.success(restored)
                    }
                }

                else -> result.notImplemented()
            }
        }
    }

    fun clear() {
        mainJob.cancel()
        channel?.setMethodCallHandler(null)
        channel = null
    }
}
