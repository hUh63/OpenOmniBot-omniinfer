package cn.com.omnimind.bot.im

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.com.omnimind.bot.R
import cn.com.omnimind.bot.activity.MainActivity

class ImChannelForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ImChannelManager.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        ImChannelManager.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        ImChannelManager.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("IMessage 连接中")
        .setContentText("正在监听已启用的微信或 Telegram 渠道")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(buildContentIntent())
        .build()

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra("route", "/home/imessage_setting")
            putExtra("needClear", false)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        return PendingIntent.getActivity(this, NOTIFICATION_ID, intent, flags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "IMessage 渠道",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "微信与 Telegram IM 渠道连接"
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "im_channel_service"
        private const val NOTIFICATION_ID = 23061
        private const val ACTION_STOP = "cn.com.omnimind.bot.im.STOP"

        fun ensureState(context: Context) {
            val appContext = context.applicationContext
            val enabled = ImChannelStore(appContext).loadSettings().anyEnabled()
            val intent = Intent(appContext, ImChannelForegroundService::class.java)
            if (enabled) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.stopService(intent)
                ImChannelManager.stop()
            }
        }
    }
}
