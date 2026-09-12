package cn.com.omnimind.assists

import android.content.Context
import cn.com.omnimind.assists.api.bean.TaskParams
import cn.com.omnimind.assists.task.TaskChangeImpl
import cn.com.omnimind.assists.task.companion.CompanionTask

class StateMachine {
    private var isInitialized = false
    private var taskManager: TaskManager? = null

    fun isInitialized(): Boolean {
        return isInitialized
    }

    fun init(context: Context) {
        taskManager = TaskManager(context, TaskChangeImpl())
        isInitialized = true
    }

    fun startTask(params: TaskParams) {
        taskManager?.createAndStartTask(params)
    }

    fun cancelChatTask(taskId: String? = null) {
        taskManager?.cancelChatTask(taskId)
    }

    /**
     * 结束陪伴
     */
    fun finishAppCompanion() {
        taskManager?.stopCompanionTask()
    }

    /**
     * 获取当前陪伴任务
     */
    fun getRunningCompanionTask(): CompanionTask? {
        return taskManager?.getCompanionTask()
    }

    /**
     * 判断是否有陪伴任务执行
     */
    fun isRunningCompanionTask(): Boolean {
        return taskManager?.getCompanionTask()?.isRunning == true
    }

    /**
     * 取消陪伴任务的回到桌面操作
     */
    fun cancelCompanionGoHome() {
        taskManager?.cancelCompanionGoHome()
    }
}
