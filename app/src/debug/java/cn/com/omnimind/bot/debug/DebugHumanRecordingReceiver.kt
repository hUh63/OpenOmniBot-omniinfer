package cn.com.omnimind.bot.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.com.omnimind.assists.HumanTrajectoryLearningSession
import cn.com.omnimind.assists.ManualInputTarget
import cn.com.omnimind.assists.ManualOverlayTouchGesture
import cn.com.omnimind.baselib.runlog.OobActionSchema
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.uikit.loader.ManualRecordingControlOverlay
import cn.com.omnimind.uikit.loader.ManualTouchRecordLoader
import com.google.gson.GsonBuilder
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugHumanRecordingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val payload = runCatching { execute(appContext, intent) }.getOrElse { error ->
                    linkedMapOf(
                        "success" to false,
                        "error_code" to "DEBUG_RECORDING_FAILED",
                        "error_message" to (error.message ?: error.javaClass.simpleName),
                    )
                }
                File(appContext.filesDir, RESULT_FILE).writeText(gson.toJson(payload))
                OmniLog.i(TAG, gson.toJson(payload))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun execute(context: Context, intent: Intent?): Map<String, Any?> {
        return when (intent.text("op").ifBlank { "status" }.lowercase()) {
            "start" -> {
                HumanTrajectoryLearningSession.start(
                    context = context,
                    name = intent.text("name").ifBlank { "设备验收录制" },
                    description = intent.text("description").ifBlank { "设备验收录制" },
                    enableRawTouch = false,
                    enableDebugScreenshots = false,
                )
                val runId = HumanTrajectoryLearningSession.activeRunId()
                val overlayShown = runId != null && withContext(Dispatchers.Main.immediate) {
                    ManualRecordingControlOverlay.show(
                        context = context,
                        runId = runId,
                        state = ManualRecordingControlOverlay.State.RECORDING,
                    ).also { shown ->
                        if (shown) ManualRecordingControlOverlay.markRecording()
                    }
                }
                if (!overlayShown && runId != null) {
                    HumanTrajectoryLearningSession.cancelActive(
                        expectedRunId = runId,
                        message = "Debug manual touch overlay unavailable",
                    )
                }
                statusPayload(overlayShown)
            }
            "status" -> statusPayload(success = true)
            "resume" -> statusPayload(HumanTrajectoryLearningSession.resumeActive())
            "resume_overlay" -> {
                val resumed = HumanTrajectoryLearningSession.resumeActive()
                if (resumed) withContext(Dispatchers.Main.immediate) {
                    ManualRecordingControlOverlay.markRecording()
                }
                statusPayload(resumed)
            }
            "pause" -> {
                val paused = HumanTrajectoryLearningSession.pauseActive()
                withContext(Dispatchers.Main.immediate) {
                    ManualRecordingControlOverlay.markPaused()
                }
                statusPayload(paused)
            }
            "gesture" -> recordGesture(intent)
            "input_text" -> {
                val text = intent.text("text")
                val target = ManualInputTarget(
                    description = intent.text("description").ifBlank { "debug input" },
                    x = intent.float("x") ?: error("x is required"),
                    y = intent.float("y") ?: error("y is required"),
                    nodeResourceId = intent.text("nodeResourceId").ifBlank { null },
                )
                statusPayload(
                    HumanTrajectoryLearningSession.recordManualInputText(text, target),
                )
            }
            "press_key" -> statusPayload(
                HumanTrajectoryLearningSession.recordManualPressKey(
                    intent.text("key").ifBlank { "enter" },
                    intent.manualInputTarget(),
                ),
            )
            "wait" -> statusPayload(
                HumanTrajectoryLearningSession.recordManualWait(intent.long("durationMs") ?: 500L),
            )
            "finish" -> {
                val runId = HumanTrajectoryLearningSession.activeRunId()
                ManualTouchRecordLoader.beginFinishing()
                val drained = ManualTouchRecordLoader.awaitIdle()
                val completed = runId != null &&
                    HumanTrajectoryLearningSession.completeActive(runId)
                withContext(Dispatchers.Main.immediate) {
                    ManualRecordingControlOverlay.dismiss()
                }
                statusPayload(completed && drained, runId) + mapOf(
                    "touch_queue_drained" to drained,
                )
            }
            else -> linkedMapOf(
                "success" to false,
                "error_code" to "UNKNOWN_OP",
                "error_message" to "Unsupported debug recording operation",
            )
        }
    }

    private suspend fun recordGesture(intent: Intent?): Map<String, Any?> {
        val action = intent.text("action").lowercase().let {
            when (it) {
                "tap", "click", "" -> OobActionSchema.TOOL_CLICK
                "swipe" -> OobActionSchema.TOOL_SWIPE
                "long_press", "longpress" -> OobActionSchema.TOOL_LONG_PRESS
                else -> it
            }
        }
        val startX = intent.float("x") ?: intent.float("x1") ?: error("x is required")
        val startY = intent.float("y") ?: intent.float("y1") ?: error("y is required")
        val endX = intent.float("x2") ?: startX
        val endY = intent.float("y2") ?: startY
        val durationMs = intent.long("durationMs")
            ?: if (action == OobActionSchema.TOOL_SWIPE) 400L else 80L
        val finishedAtMs = System.currentTimeMillis()
        val replay = HumanTrajectoryLearningSession.recordOverlayGesture(
            ManualOverlayTouchGesture(
                actionName = action,
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                durationMs = durationMs,
                distancePx = sqrt(
                    (endX - startX).toDouble().pow(2.0) +
                        (endY - startY).toDouble().pow(2.0),
                ).toFloat(),
                direction = null,
                startedAtMs = finishedAtMs - durationMs,
                finishedAtMs = finishedAtMs,
                displayWidth = intent.int("displayWidth") ?: 1080,
                displayHeight = intent.int("displayHeight") ?: 2400,
            ),
        )
        return statusPayload(replay.executed && replay.recorded) + mapOf(
            "executed" to replay.executed,
            "recorded" to replay.recorded,
            "ignored_control" to replay.ignoredControl,
        )
    }

    private fun statusPayload(success: Boolean, completedRunId: String? = null): Map<String, Any?> {
        val status = HumanTrajectoryLearningSession.status()
        return linkedMapOf(
            "success" to success,
            "run_id" to (completedRunId ?: status.runId),
            "recording_active" to status.active,
            "recording_paused" to status.paused,
            "action_count" to status.actionCount,
            "latest_action_summary" to status.latestActionSummary,
            "recording_backend" to status.recordingBackend,
        ).filterValues { it != null }
    }

    private fun Intent?.text(name: String): String =
        this?.getStringExtra(name)?.trim().orEmpty()

    private fun Intent?.float(name: String): Float? = when (val value = this?.extras?.get(name)) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull()
        else -> null
    }

    private fun Intent?.long(name: String): Long? = when (val value = this?.extras?.get(name)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    private fun Intent?.manualInputTarget(): ManualInputTarget? {
        val x = float("x") ?: return null
        val y = float("y") ?: return null
        return ManualInputTarget(
            description = text("description").ifBlank { "debug input" },
            x = x,
            y = y,
            nodeResourceId = text("nodeResourceId").ifBlank { null },
        )
    }

    private fun Intent?.int(name: String): Int? = when (val value = this?.extras?.get(name)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private companion object {
        const val TAG = "DebugHumanRecording"
        const val RESULT_FILE = "debug-human-recording-result.json"
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
