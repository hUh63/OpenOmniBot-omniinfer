package cn.com.omnimind.bot.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.com.omnimind.baselib.util.OmniLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkspaceScheduledTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != WorkspaceScheduledTaskScheduler.ACTION_SCHEDULED_TASK_TRIGGER) {
            return
        }
        val taskId = intent.getStringExtra(WorkspaceScheduledTaskScheduler.EXTRA_TASK_ID)
            ?.trim()
            .orEmpty()
        if (taskId.isEmpty()) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WorkspaceScheduledTaskScheduler(context).onAlarmTriggered(taskId)
            } catch (t: Throwable) {
                OmniLog.e(
                    "WorkspaceTaskReceiver",
                    "trigger failed taskId=$taskId message=${t.message}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
