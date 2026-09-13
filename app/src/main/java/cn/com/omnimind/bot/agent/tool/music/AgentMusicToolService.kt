package cn.com.omnimind.bot.agent

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import cn.com.omnimind.bot.workspace.PublicStorageAccess
import java.io.File

data class AgentMusicPlayRequest(
    val source: String?,
    val title: String?,
    val loop: Boolean
)

data class AgentMusicPlaybackSnapshot(
    val state: String = AgentMusicPlaybackStateStore.STATE_IDLE,
    val source: String? = null,
    val playbackTarget: String? = null,
    val title: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val isLooping: Boolean = false,
    val backend: String = AgentMusicPlaybackStateStore.BACKEND_OMNIBOT,
    val lastError: String? = null,
    val updatedAtMillis: Long = 0L
) {
    fun toPayload(): Map<String, Any?> = linkedMapOf(
        "state" to state,
        "source" to source,
        "playbackTarget" to playbackTarget,
        "title" to title,
        "positionMs" to positionMs,
        "positionSeconds" to positionMs / 1000,
        "durationMs" to durationMs,
        "durationSeconds" to durationMs?.div(1000),
        "isLooping" to isLooping,
        "backend" to backend,
        "lastError" to lastError,
        "updatedAtMillis" to updatedAtMillis
    )
}

object AgentMusicPlaybackStateStore {
    const val STATE_IDLE = "idle"
    const val STATE_PREPARING = "preparing"
    const val STATE_PLAYING = "playing"
    const val STATE_PAUSED = "paused"
    const val STATE_STOPPED = "stopped"
    const val STATE_COMPLETED = "completed"
    const val STATE_ERROR = "error"

    const val BACKEND_OMNIBOT = "omnibot_service"
    const val BACKEND_SYSTEM_MEDIA_KEY = "system_media_key"

    private val lock = Any()
    private var currentSnapshot = AgentMusicPlaybackSnapshot(
        updatedAtMillis = System.currentTimeMillis()
    )

    fun snapshot(): AgentMusicPlaybackSnapshot = synchronized(lock) {
        currentSnapshot.copy()
    }

    fun set(snapshot: AgentMusicPlaybackSnapshot) {
        synchronized(lock) {
            currentSnapshot = snapshot.copy(
                updatedAtMillis = snapshot.updatedAtMillis.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
            )
        }
    }

    fun update(transform: (AgentMusicPlaybackSnapshot) -> AgentMusicPlaybackSnapshot) {
        synchronized(lock) {
            val updated = transform(currentSnapshot)
            currentSnapshot = updated.copy(
                updatedAtMillis = updated.updatedAtMillis.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
            )
        }
    }
}

private data class ResolvedMusicSource(
    val playbackTarget: String,
    val displayTitle: String
)

class AgentMusicToolService(
    private val context: Context,
    private val workspaceManager: AgentWorkspaceManager
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun play(
        request: AgentMusicPlayRequest,
        workspace: AgentWorkspaceDescriptor
    ): Map<String, Any?> {
        val rawSource = request.source?.trim().orEmpty()
        if (rawSource.isBlank()) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            return linkedMapOf(
                "success" to true,
                "summary" to "已发送系统播放命令。",
                "backend" to AgentMusicPlaybackStateStore.BACKEND_SYSTEM_MEDIA_KEY,
                "command" to "play"
            )
        }

        val resolved = resolveMusicSource(rawSource, workspace)
        val title = request.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: resolved.displayTitle
        val snapshot = AgentMusicPlaybackSnapshot(
            state = AgentMusicPlaybackStateStore.STATE_PREPARING,
            source = rawSource,
            playbackTarget = resolved.playbackTarget,
            title = title,
            positionMs = 0L,
            durationMs = null,
            isLooping = request.loop,
            backend = AgentMusicPlaybackStateStore.BACKEND_OMNIBOT,
            lastError = null
        )
        AgentMusicPlaybackStateStore.set(snapshot)
        AgentMusicPlaybackService.startPlayback(
            context = context,
            source = rawSource,
            playbackTarget = resolved.playbackTarget,
            title = title,
            loop = request.loop
        )
        return buildResponse(
            summary = "正在准备播放 $title。",
            snapshot = snapshot
        )
    }

    fun pause(): Map<String, Any?> {
        val snapshot = AgentMusicPlaybackStateStore.snapshot()
        if (canPauseOrStopLocalPlayback(snapshot)) {
            val updated = snapshot.copy(
                state = AgentMusicPlaybackStateStore.STATE_PAUSED
            )
            AgentMusicPlaybackStateStore.set(updated)
            AgentMusicPlaybackService.pause(context)
            return buildResponse("已暂停当前音频。", snapshot = updated)
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        return linkedMapOf(
            "success" to true,
            "summary" to "已发送系统暂停命令。",
            "backend" to AgentMusicPlaybackStateStore.BACKEND_SYSTEM_MEDIA_KEY,
            "command" to "pause"
        )
    }

    fun resume(): Map<String, Any?> {
        val snapshot = AgentMusicPlaybackStateStore.snapshot()
        if (canResumeLocalPlayback(snapshot)) {
            val updated = snapshot.copy(
                state = AgentMusicPlaybackStateStore.STATE_PLAYING
            )
            AgentMusicPlaybackStateStore.set(updated)
            AgentMusicPlaybackService.resume(context)
            return buildResponse("已恢复播放。", snapshot = updated)
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        return linkedMapOf(
            "success" to true,
            "summary" to "已发送系统继续播放命令。",
            "backend" to AgentMusicPlaybackStateStore.BACKEND_SYSTEM_MEDIA_KEY,
            "command" to "resume"
        )
    }

    fun stop(): Map<String, Any?> {
        val snapshot = AgentMusicPlaybackStateStore.snapshot()
        if (canPauseOrStopLocalPlayback(snapshot)) {
            val updated = snapshot.copy(
                state = AgentMusicPlaybackStateStore.STATE_STOPPED
            )
            AgentMusicPlaybackStateStore.set(updated)
            AgentMusicPlaybackService.stop(context)
            return buildResponse("已停止当前音频。", snapshot = updated)
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
        return linkedMapOf(
            "success" to true,
            "summary" to "已发送系统停止命令。",
            "backend" to AgentMusicPlaybackStateStore.BACKEND_SYSTEM_MEDIA_KEY,
            "command" to "stop"
        )
    }

    fun seek(positionSeconds: Int): Map<String, Any?> {
        require(positionSeconds >= 0) { "positionSeconds 不能为负数" }
        val snapshot = AgentMusicPlaybackStateStore.snapshot()
        require(canSeekLocalPlayback(snapshot)) { "当前没有可定位的 Omnibot 音频播放" }

        val updated = snapshot.copy(positionMs = positionSeconds * 1000L)
        AgentMusicPlaybackStateStore.set(updated)
        AgentMusicPlaybackService.seekTo(context, updated.positionMs)
        return buildResponse(
            summary = "已跳转到 ${positionSeconds} 秒。",
            snapshot = updated
        )
    }

    fun next(): Map<String, Any?> {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        return linkedMapOf(
            "success" to true,
            "summary" to "已发送系统下一首命令。",
            "backend" to AgentMusicPlaybackStateStore.BACKEND_SYSTEM_MEDIA_KEY,
            "command" to "next"
        )
    }

    fun previous(): Map<String, Any?> {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        return linkedMapOf(
            "success" to true,
            "summary" to "已发送系统上一首命令。",
            "backend" to AgentMusicPlaybackStateStore.BACKEND_SYSTEM_MEDIA_KEY,
            "command" to "previous"
        )
    }

    fun status(): Map<String, Any?> {
        val snapshot = AgentMusicPlaybackStateStore.snapshot()
        val summary = when (snapshot.state) {
            AgentMusicPlaybackStateStore.STATE_PREPARING -> {
                "正在准备播放 ${snapshot.title ?: "音频"}。"
            }

            AgentMusicPlaybackStateStore.STATE_PLAYING -> {
                "正在播放 ${snapshot.title ?: "音频"}。"
            }

            AgentMusicPlaybackStateStore.STATE_PAUSED -> {
                "${snapshot.title ?: "音频"} 已暂停。"
            }

            AgentMusicPlaybackStateStore.STATE_COMPLETED -> {
                "${snapshot.title ?: "音频"} 已播放完成。"
            }

            AgentMusicPlaybackStateStore.STATE_STOPPED -> {
                "${snapshot.title ?: "音频"} 已停止。"
            }

            AgentMusicPlaybackStateStore.STATE_ERROR -> {
                snapshot.lastError?.takeIf { it.isNotBlank() }
                    ?: "最近一次播放出现错误。"
            }

            else -> "当前没有 Omnibot 托管的音频播放。"
        }
        return buildResponse(summary = summary, snapshot = snapshot)
    }

    private fun buildResponse(
        summary: String,
        snapshot: AgentMusicPlaybackSnapshot
    ): Map<String, Any?> = linkedMapOf<String, Any?>(
        "success" to true,
        "summary" to summary
    ).apply {
        putAll(snapshot.toPayload())
    }

    private fun canPauseOrStopLocalPlayback(snapshot: AgentMusicPlaybackSnapshot): Boolean {
        return snapshot.backend == AgentMusicPlaybackStateStore.BACKEND_OMNIBOT &&
            !snapshot.playbackTarget.isNullOrBlank() &&
            snapshot.state in setOf(
                AgentMusicPlaybackStateStore.STATE_PREPARING,
                AgentMusicPlaybackStateStore.STATE_PLAYING,
                AgentMusicPlaybackStateStore.STATE_PAUSED
            )
    }

    private fun canResumeLocalPlayback(snapshot: AgentMusicPlaybackSnapshot): Boolean {
        return snapshot.backend == AgentMusicPlaybackStateStore.BACKEND_OMNIBOT &&
            !snapshot.playbackTarget.isNullOrBlank() &&
            snapshot.state == AgentMusicPlaybackStateStore.STATE_PAUSED
    }

    private fun canSeekLocalPlayback(snapshot: AgentMusicPlaybackSnapshot): Boolean {
        return snapshot.backend == AgentMusicPlaybackStateStore.BACKEND_OMNIBOT &&
            !snapshot.playbackTarget.isNullOrBlank() &&
            snapshot.state in setOf(
                AgentMusicPlaybackStateStore.STATE_PLAYING,
                AgentMusicPlaybackStateStore.STATE_PAUSED
            )
    }

    private fun resolveMusicSource(
        rawSource: String,
        workspace: AgentWorkspaceDescriptor
    ): ResolvedMusicSource {
        val trimmed = rawSource.trim()
        require(trimmed.isNotEmpty()) { "source 不能为空" }

        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> {
                ResolvedMusicSource(
                    playbackTarget = trimmed,
                    displayTitle = deriveTitleFromSource(trimmed)
                )
            }

            trimmed.startsWith("content://", ignoreCase = true) -> {
                ResolvedMusicSource(
                    playbackTarget = trimmed,
                    displayTitle = deriveTitleFromSource(trimmed)
                )
            }

            trimmed.startsWith("file://", ignoreCase = true) -> {
                val uri = Uri.parse(trimmed)
                val path = uri.path?.takeIf { it.isNotBlank() }
                if (path != null) {
                    val file = File(path)
                    if (file.exists() && !file.isDirectory) {
                        return ResolvedMusicSource(
                            playbackTarget = trimmed,
                            displayTitle = file.name
                        )
                    }
                }
                ResolvedMusicSource(
                    playbackTarget = trimmed,
                    displayTitle = deriveTitleFromSource(trimmed)
                )
            }

            else -> {
                val file = resolveLocalFile(trimmed, workspace)
                require(file.exists()) { "音频文件不存在：$rawSource" }
                require(!file.isDirectory) { "source 必须是音频文件而不是目录" }
                ResolvedMusicSource(
                    playbackTarget = file.absolutePath,
                    displayTitle = file.name
                )
            }
        }
    }

    private fun resolveLocalFile(
        inputPath: String,
        workspace: AgentWorkspaceDescriptor
    ): File {
        return runCatching {
            workspaceManager.resolvePath(
                inputPath,
                workspace,
                allowPublicStorage = true
            )
        }.getOrElse { originalError ->
            val direct = File(inputPath)
            if (direct.isAbsolute && direct.exists()) {
                direct.canonicalFile
            } else {
                throw originalError
            }
        }
    }

    private fun deriveTitleFromSource(source: String): String {
        if (PublicStorageAccess.isPublicStoragePath(source)) {
            return File(source).name.ifBlank { "音频" }
        }
        return runCatching {
            val uri = Uri.parse(source)
            uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        }.getOrNull()?.let { Uri.decode(it) } ?: source.substringAfterLast('/').ifBlank { "音频" }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val eventTime = System.currentTimeMillis()
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        )
    }
}
