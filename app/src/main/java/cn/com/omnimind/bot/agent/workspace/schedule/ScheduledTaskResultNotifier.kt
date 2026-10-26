package cn.com.omnimind.bot.agent.workspace.schedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.activity.MainActivity

/**
 * 后台定时任务跑完/失败时的通知。
 *
 * 任务里的「通知」开关以前是死参数：调度器把 scheduledTaskId / scheduledTaskTitle /
 * scheduleNotificationEnabled 塞进 args，但 WebAgentRunBridge.startRun 根本不读它们，
 * 也没有任何代码发通知。用户设了任务、退出 App，跑完之后什么都收不到，只能自己进会话
 * 翻——失败时连会话里都可能只有自己那条消息。
 */
object ScheduledTaskResultNotifier {
    private const val TAG = "ScheduledTaskNotify"
    private const val CHANNEL_ID = "scheduled_task_result"
    private const val CHANNEL_NAME = "定时任务结果"

    /** 调度器给 run 打的 taskId 前缀，用来识别「这是定时任务触发的 run」。 */
    const val RUN_TASK_ID_PREFIX = "subagent_schedule_"

    /**
     * 任务没有按时执行（错过触发时间被停用 / 启动就失败）时必须让用户知道：
     * 静默停用是最难排查的一种，用户会以为任务还在按计划跑。
     */
    fun notifyProblem(
        context: Context,
        taskId: String,
        title: String?,
        summary: String
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(CHANNEL_ID) == null
            ) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                taskId.hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("scheduledTaskId", taskId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title?.trim()?.takeIf { it.isNotEmpty() } ?: "定时任务")
                .setContentText(summary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        } catch (error: Exception) {
            OmniLog.w(TAG, "notify scheduled task problem failed: ${error.message}")
        }
    }

    fun notifyResult(
        context: Context,
        conversationId: Long,
        taskId: String,
        success: Boolean,
        summary: String?
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                OmniLog.w(TAG, "POST_NOTIFICATIONS not granted; skip scheduled task result")
                return
            }
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(CHANNEL_ID) == null
            ) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val body = summary?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(160)
                ?: if (success) "任务已完成，点击查看结果" else "任务执行失败，点击查看详情"
            val openIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("conversationId", conversationId)
                putExtra("scheduledTaskId", taskId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                taskId.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(if (success) "定时任务已完成" else "定时任务执行失败")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        } catch (error: Exception) {
            OmniLog.w(TAG, "notify scheduled task result failed: ${error.message}")
        }
    }
}
