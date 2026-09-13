package cn.com.omnimind.assists.api.eventapi

import cn.com.omnimind.assists.api.enums.TaskFinishType

interface TaskEventApi {


    /**
     * 陪伴模式开启
     */
    suspend fun startTask(isCompanionRunning: Boolean)

    /**
     * 陪伴模式结束
     */
    suspend fun onTaskStop(
        finishType: TaskFinishType,
        message: String,
        isCompanionRunning: Boolean
    )
}