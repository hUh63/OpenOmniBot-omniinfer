package cn.com.omnimind.assists.api.interfaces

import cn.com.omnimind.assists.api.enums.TaskFinishType

/**
 * 任务生命周期监听器
 */
interface TaskLifeListener {
    suspend fun onTaskCreated();//任务已创建
    suspend  fun onTaskStarted();//任务开始执行
    suspend fun onTaskStop(finishType: TaskFinishType,message: String);//任务被停止
    suspend fun onTaskDestroy();//任务已销毁
}