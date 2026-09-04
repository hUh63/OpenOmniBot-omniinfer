package cn.com.omnimind.assists.api.interfaces

import cn.com.omnimind.assists.TaskManager
import cn.com.omnimind.assists.api.enums.TaskFinishType
import cn.com.omnimind.assists.api.enums.TaskType

interface TaskChangeListener {
    suspend fun onTaskStart(taskType: TaskType,taskManager: TaskManager)
    suspend fun onTaskStop(taskType: TaskType,finishType: TaskFinishType=TaskFinishType.FINISH,message:String="",taskManager: TaskManager)
}
