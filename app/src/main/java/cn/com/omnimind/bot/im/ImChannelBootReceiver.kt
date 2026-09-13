package cn.com.omnimind.bot.im

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ImChannelBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> ImChannelForegroundService.ensureState(context)
        }
    }
}
