package cn.com.omnimind.assists.api.interfaces

interface OnMessagePushListener {
    /**
     * 大模型消息
     * @param taskID 任务ID
     * @param content 消息内容
     * @param type 消息类型（来自EventSource的type字段）
     */
    suspend fun onChatMessage(taskID:String,content: String, type: String?)

    /**
     * 大模型消息结束事件
     */
    suspend fun onChatMessageEnd(taskID:String)

    /**
     * 任务结束回调
     */
    fun onTaskFinish()
}
