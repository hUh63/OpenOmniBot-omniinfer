package cn.com.omnimind.assists

import android.content.Context
import cn.com.omnimind.accessibility.service.AssistsService
import cn.com.omnimind.assists.api.bean.TaskParams

/**
 * Facade for the remaining chat task lifecycle.
 */
object AssistsCore {

    const val TAG = "[Assists]"
    private var stateMachine: StateMachine? = null

    fun initCore(context: Context) {
        stateMachine = StateMachine().also { it.init(context.applicationContext) }
    }

    fun isStateMachineInitialized(): Boolean {
        return stateMachine?.isInitialized() == true
    }

    fun isAccessibilityServiceEnabled(): Boolean = AssistsService.instance != null

    fun startTask(params: TaskParams) {
        stateMachine?.startTask(params)
    }

    fun cancelChatTask(taskId: String? = null) {
        stateMachine?.cancelChatTask(taskId)
    }

    fun isCompanionTaskRunning(): Boolean = stateMachine?.isRunningCompanionTask() ?: false

    fun finishCompanionTask() = stateMachine?.finishAppCompanion()

    fun cancelCompanionGoHome() = stateMachine?.cancelCompanionGoHome()
}
