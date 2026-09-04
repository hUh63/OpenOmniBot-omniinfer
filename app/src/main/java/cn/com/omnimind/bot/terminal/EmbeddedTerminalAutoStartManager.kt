package cn.com.omnimind.bot.terminal

import android.content.Context
import cn.com.omnimind.baselib.util.OmniLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class EmbeddedTerminalAutoStartManager(
    private val context: Context
) {
    data class AutoStartTaskSnapshot(
        val id: String,
        val name: String,
        val command: String,
        val workingDirectory: String?,
        val enabled: Boolean,
        val running: Boolean,
        val sessionId: String
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "id" to id,
            "name" to name,
            "command" to command,
            "workingDirectory" to workingDirectory,
            "enabled" to enabled,
            "running" to running,
            "sessionId" to sessionId
        )
    }

    data class TaskRunResult(
        val taskId: String,
        val started: Boolean,
        val alreadyRunning: Boolean,
        val message: String,
        val sessionId: String
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "taskId" to taskId,
            "started" to started,
            "alreadyRunning" to alreadyRunning,
            "message" to message,
            "sessionId" to sessionId
        )
    }

    @Serializable
    private data class StoredAutoStartTask(
        val id: String,
        val name: String,
        val command: String,
        val workingDirectory: String? = null,
        val enabled: Boolean = true
    )

    companion object {
        private const val TAG = "EmbeddedTerminalAutoStart"
        private const val PREFS_NAME = "embedded_terminal_auto_start"
        private const val KEY_TASKS_JSON = "tasks_json"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun listTasks(): List<AutoStartTaskSnapshot> {
        return loadTasks().map { task ->
            task.toSnapshot(
                running = ReTerminalSessionBridge.getSession(context, task.sessionId())?.isRunning == true
            )
        }
    }

    suspend fun saveTask(
        id: String?,
        name: String,
        command: String,
        workingDirectory: String?,
        enabled: Boolean
    ): AutoStartTaskSnapshot {
        val normalizedName = name.trim()
        val normalizedCommand = command.trim()
        val normalizedWorkingDirectory = workingDirectory?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedName.isNotEmpty()) { "名称不能为空" }
        require(normalizedCommand.isNotEmpty()) { "命令不能为空" }

        val tasks = loadTasks().toMutableList()
        val existingIndex = id?.trim()?.takeIf { it.isNotEmpty() }?.let { taskId ->
            tasks.indexOfFirst { it.id == taskId }
        } ?: -1

        val storedTask = StoredAutoStartTask(
            id = if (existingIndex >= 0) tasks[existingIndex].id else UUID.randomUUID().toString(),
            name = normalizedName,
            command = normalizedCommand,
            workingDirectory = normalizedWorkingDirectory,
            enabled = enabled
        )

        if (existingIndex >= 0) {
            tasks[existingIndex] = storedTask
        } else {
            tasks.add(storedTask)
        }
        persistTasks(tasks)
        return storedTask.toSnapshot(
            running = ReTerminalSessionBridge.getSession(context, storedTask.sessionId())?.isRunning == true
        )
    }

    suspend fun deleteTask(taskId: String) {
        val normalizedTaskId = taskId.trim()
        if (normalizedTaskId.isEmpty()) {
            return
        }
        val tasks = loadTasks()
        val task = tasks.firstOrNull { it.id == normalizedTaskId } ?: return
        persistTasks(tasks.filterNot { it.id == normalizedTaskId })
        runCatching {
            ReTerminalSessionBridge.stopSession(context, task.sessionId())
        }
    }

    suspend fun runTaskNow(taskId: String): TaskRunResult {
        val normalizedTaskId = taskId.trim()
        val task = loadTasks().firstOrNull { it.id == normalizedTaskId }
            ?: return TaskRunResult(
                taskId = normalizedTaskId,
                started = false,
                alreadyRunning = false,
                message = "未找到对应的自启动任务。",
                sessionId = ""
            )
        return launchTask(task)
    }

    suspend fun runEnabledTasksOnAppOpen() {
        loadTasks()
            .asSequence()
            .filter { it.enabled }
            .forEach { task ->
                runCatching {
                    launchTask(task)
                }.onFailure { error ->
                    OmniLog.e(
                        TAG,
                        "Failed to start terminal auto-start task: ${task.name}",
                        error
                    )
                }
            }
    }

    private suspend fun launchTask(task: StoredAutoStartTask): TaskRunResult {
        val sessionId = task.sessionId()
        val result = EmbeddedTerminalRuntime.launchBackgroundServiceSession(
            context = context,
            sessionId = sessionId,
            command = task.command,
            workingDirectory = task.workingDirectory
        )
        return TaskRunResult(
            taskId = task.id,
            started = result.started,
            alreadyRunning = result.alreadyRunning,
            message = result.message,
            sessionId = sessionId
        )
    }

    private fun loadTasks(): List<StoredAutoStartTask> {
        val raw = prefs.getString(KEY_TASKS_JSON, null)?.trim().orEmpty()
        if (raw.isEmpty()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<StoredAutoStartTask>>(raw)
        }.getOrElse {
            OmniLog.e(TAG, "Failed to parse terminal auto-start tasks", it)
            emptyList()
        }
    }

    private fun persistTasks(tasks: List<StoredAutoStartTask>) {
        prefs.edit().putString(KEY_TASKS_JSON, json.encodeToString(tasks)).apply()
    }

    private fun StoredAutoStartTask.toSnapshot(running: Boolean): AutoStartTaskSnapshot {
        return AutoStartTaskSnapshot(
            id = id,
            name = name,
            command = command,
            workingDirectory = workingDirectory,
            enabled = enabled,
            running = running,
            sessionId = sessionId()
        )
    }

    private fun StoredAutoStartTask.sessionId(): String {
        val normalizedId = id.lowercase()
            .replace(Regex("[^a-z0-9]+"), "")
            .take(24)
            .ifEmpty { "task" }
        return "autostart_$normalizedId"
    }
}
