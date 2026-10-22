package cn.com.omnimind.bot.webchat

import cn.com.omnimind.baselib.util.OmniLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class RealtimeEvent(
    val id: String,
    val event: String,
    val data: Map<String, Any?>,
    val timestamp: Long
)

object RealtimeHub {
    private const val TAG = "RealtimeHub"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // 无订阅者（webchat 没连过来）时，向无缓冲的 SharedFlow emit 会挂起；消费者
    // 一卡住，下面 UNLIMITED 的队列就会一直堆积，内存持续增长。实时事件过期即无
    // 价值，所以给有限缓冲 + 丢弃最旧。
    private val events = MutableSharedFlow<RealtimeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val pendingEvents = Channel<RealtimeEvent>(Channel.BUFFERED)

    init {
        scope.launch {
            for (event in pendingEvents) {
                events.emit(event)
            }
        }
    }

    fun stream(): SharedFlow<RealtimeEvent> = events.asSharedFlow()

    fun publish(
        event: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        val timestamp = System.currentTimeMillis()
        val payload = linkedMapOf<String, Any?>(
            "event" to event,
            "timestamp" to timestamp
        ).apply {
            putAll(data)
        }
        val wrapped = RealtimeEvent(
            id = UUID.randomUUID().toString(),
            event = event,
            data = payload,
            timestamp = timestamp
        )
        val queued = pendingEvents.trySend(wrapped)
        if (!queued.isSuccess) {
            // 实时推送丢一条不影响一致性（客户端可重新拉取快照），不要因此抛异常
            // 把调用方（例如会话写入路径）打断。
            OmniLog.w(TAG, "Realtime event dropped: ${queued.exceptionOrNull()}")
        }
    }
}
