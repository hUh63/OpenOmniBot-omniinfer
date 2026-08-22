package cn.com.omnimind.assists.task

import cn.com.omnimind.assists.TaskManager
import cn.com.omnimind.assists.api.enums.TaskFinishType
import cn.com.omnimind.assists.api.enums.TaskType
import cn.com.omnimind.assists.api.interfaces.TaskChangeListener

class TaskChangeImpl : TaskChangeListener {

    override suspend fun onTaskStart(
        taskType: TaskType, taskManager: TaskManager
    ) {
        Unit
    }

    override suspend fun onTaskStop(
        taskType: TaskType, finishType: TaskFinishType, message: String, taskManager: TaskManager
    ) {
        Unit
    }
}
