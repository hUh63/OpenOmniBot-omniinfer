package cn.com.omnimind.bot.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import cn.com.omnimind.bot.agent.HttpAgentLlmClient
import cn.com.omnimind.bot.omniflow.OmniVlmPlugin
import cn.com.omnimind.bot.omniflow.asOmniFlowModelClient
import com.google.gson.GsonBuilder
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

class DebugVlmTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        Log.i(TAG, "vlm_task_received")
        scope.launch {
            val runId = "debug-vlm-${UUID.randomUUID()}"
            val payload = runCatching {
                    val goal = intent?.getStringExtra("goalBase64")
                        ?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }
                        ?.trim()
                        .orEmpty()
                        .ifBlank { intent?.getStringExtra("goal")?.trim().orEmpty() }
                    require(goal.isNotEmpty()) { "goal is required" }
                    Log.i(TAG, "vlm_task_execute runId=$runId")
                    val result = OmniVlmPlugin.execute(
                        context = appContext,
                        request = OmniVlmPlugin.Request(
                            goal = goal,
                            runId = runId,
                        ),
                        modelClient = HttpAgentLlmClient(CoroutineScope(currentCoroutineContext()))
                            .asOmniFlowModelClient(),
                    )
                    val resultRunId = result.payload["run_id"]?.toString()?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?: runId
                    result.payload + mapOf(
                        "run_id" to resultRunId,
                        "final_state_id" to result.finalStateId,
                    )
                }.getOrElse { error ->
                    linkedMapOf(
                        "success" to false,
                        "run_id" to runId,
                        "error_message" to (error.message ?: error.javaClass.simpleName),
                        "error_type" to error.javaClass.name,
                    )
                }
            File(appContext.filesDir, RESULT_FILE).writeText(gson.toJson(payload))
            Log.i(TAG, "vlm_task_finished success=${payload["success"]}")
        }
    }

    private companion object {
        const val RESULT_FILE = "debug-vlm-task-result.json"
        const val TAG = "DebugVlmTaskReceiver"
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
