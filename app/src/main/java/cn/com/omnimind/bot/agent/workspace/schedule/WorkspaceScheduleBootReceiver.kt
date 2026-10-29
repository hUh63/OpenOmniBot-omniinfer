package cn.com.omnimind.bot.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.com.omnimind.baselib.util.OmniLog

class WorkspaceScheduleBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED
        ) {
            return
        }
        // 三件事互相独立，必须各自兜底：共用一次 runCatching 时，前面抛异常（如记忆
        // 汇总重排失败）会让后面的提醒闹钟恢复被整段跳过 —— 重启后闹钟永久不再响。
        runCatching { WorkspaceMemoryRollupScheduler(context).ensureScheduledIfEnabled() }
            .onFailure {
                OmniLog.e("WorkspaceScheduleBootReceiver", "memory rollup reschedule failed: ${it.message}")
            }
        runCatching { WorkspaceScheduledTaskScheduler(context).rescheduleAllEnabled() }
            .onFailure {
                OmniLog.e("WorkspaceScheduleBootReceiver", "scheduled task reschedule failed: ${it.message}")
            }
        runCatching { AgentAlarmToolService(context).reschedulePersistedExactRemindersIfPermitted() }
            .onFailure {
                OmniLog.e("WorkspaceScheduleBootReceiver", "exact alarm reschedule failed: ${it.message}")
            }
    }
}
