package cn.com.omnimind.assists

import android.content.Context
import cn.com.omnimind.assists.api.bean.TaskParams
import cn.com.omnimind.assists.api.interfaces.TaskChangeListener
import cn.com.omnimind.assists.task.ChatTask
import cn.com.omnimind.assists.task.companion.CompanionTask
import cn.com.omnimind.baselib.util.OmniLog

class TaskManager(
    val context: Context,
    val taskChangeListener: TaskChangeListener
) {

    private val TAG = "[Assists] TaskManager"
    private val chatTasks: LinkedHashMap<String, ChatTask> = linkedMapOf()
    private var companionTask: CompanionTask? = null;//陪伴任务

    fun createAndStartTask(params: TaskParams) {
        when (params) {
            is TaskParams.ChatTaskParams -> createChatTaskAndStart(params)
            is TaskParams.CompanionTaskParams -> createCompanionTaskAndStart(params)
        }
    }

    fun getCompanionTask(): CompanionTask? {
        return companionTask
    }

    fun isCompanionRunning(): Boolean {
        return companionTask?.isRunning == true
    }

    fun resumeCompanionTask() {
        if (companionTask?.isRunning == true) {
            // 有陪伴模式：恢复陪伴模式
            companionTask?.resumeTask()
        }
    }

    /**
     * 取消陪伴任务的回到桌面操作
     * 当用户在开启陪伴后离开主页时调用
     */
    fun cancelCompanionGoHome() {
        companionTask?.cancelGoHome()
    }

    private fun createCompanionTaskAndStart(params: TaskParams.CompanionTaskParams) {
        if (companionTask?.isRunning == true) {
            OmniLog.w(
                TAG, "CreateTask is not worked! There has a running task! Please finish it first!"
            )
            return
        }
        companionTask = CompanionTask(taskChangeListener, null, this)
        companionTask!!.start(params.companionFinishListener) {}
    }

    fun stopCompanionTask() {
        stopAllTask()
    }

    private fun stopAllTask() {
        companionTask?.finishTask() {}
    }

    fun pauseCompanionTaskRunning() {
        if (companionTask?.isRunning == true) {
            companionTask?.pauseTask()
        }
    }

    private fun createChatTaskAndStart(params: TaskParams.ChatTaskParams) {
        cleanupFinishedChatTasks()
        if (chatTasks[params.taskId]?.isRunning == true) {
            OmniLog.w(
                TAG, "ChatTask is not worked! taskId=${params.taskId} already running"
            )
            return
        }
        val chatTask = ChatTask(taskChangeListener,this)
        chatTasks[params.taskId] = chatTask
        chatTask.start(
            params.taskId,
            params.content,
            params.onMessagePush,
            params.provider,
            params.openClawConfig,
            params.modelOverride,
            params.reasoningEffort,
            params.promptCacheKey
        )
    }

    fun cancelChatTask(taskId: String? = null) {
        cleanupFinishedChatTasks()
        val targetChatTask = if (taskId.isNullOrBlank()) {
            chatTasks.values.lastOrNull { it.isRunning }
        } else {
            chatTasks[taskId]
        }
        if (targetChatTask?.isRunning == true) {
            targetChatTask.finishTask()
        }
    }

    fun unregisterChatTask(taskId: String) {
        chatTasks.remove(taskId)
    }

    private fun cleanupFinishedChatTasks() {
        val iterator = chatTasks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.value.isRunning) {
                iterator.remove()
            }
        }
    }
}
