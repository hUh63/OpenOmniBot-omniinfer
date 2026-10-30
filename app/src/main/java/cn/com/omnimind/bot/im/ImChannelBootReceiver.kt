package cn.com.omnimind.bot.im

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.com.omnimind.baselib.util.OmniLog

class ImChannelBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Android 15 起禁止从 BOOT_COMPLETED 启动 dataSync 型前台服务，系统会在
                // 调用点抛 ForegroundServiceStartNotAllowedException —— 不兜住就是开机即崩，
                // 崩完也没有任何重试路径，IM 渠道再也不恢复。这里兜住异常（渠道会在用户
                // 下次打开 App / 切换渠道时由 ensureState 重新拉起）。
                runCatching { ImChannelForegroundService.ensureState(context) }
                    .onFailure { error ->
                        OmniLog.e(
                            TAG,
                            "boot-time IM channel restart skipped: ${error.message}",
                            error
                        )
                    }
            }
        }
    }

    companion object {
        private const val TAG = "ImChannelBootReceiver"
    }
}
