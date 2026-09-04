package cn.com.omnimind.bot.agent

interface AgentScheduleToolBridge {
    suspend fun createTask(arguments: Map<String, Any?>): Map<String, Any?>

    suspend fun listTasks(): List<Map<String, Any?>>

    suspend fun updateTask(arguments: Map<String, Any?>): Map<String, Any?>

    suspend fun deleteTask(arguments: Map<String, Any?>): Map<String, Any?>
}
