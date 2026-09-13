package cn.com.omnimind.bot.ui.channel

import android.content.Context
import android.os.Handler
import android.os.Looper
import cn.com.omnimind.bot.plugin.official.OmniLinkAgentEventBus
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel

/** Bridges sanitized OmniLink Agent events into the visible Xiaowan chat. */
class OmniLinkPluginChannel {
    companion object {
        private const val EVENT_CHANNEL = "cn.com.omnimind.bot/OmniLinkEvents"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var eventChannel: EventChannel? = null
    private var eventSink: EventChannel.EventSink? = null
    private var unsubscribe: (() -> Unit)? = null

    fun onCreate(context: Context) {
        this.context = context.applicationContext
    }

    fun setChannel(flutterEngine: FlutterEngine) {
        eventChannel = EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
        eventChannel?.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                eventSink = events
                unsubscribe?.invoke()
                unsubscribe = OmniLinkAgentEventBus.subscribe { payload ->
                    mainHandler.post { eventSink?.success(payload) }
                }
            }

            override fun onCancel(arguments: Any?) {
                unsubscribe?.invoke()
                unsubscribe = null
                eventSink = null
            }
        })
    }

    fun clear() {
        unsubscribe?.invoke()
        unsubscribe = null
        eventSink = null
        eventChannel?.setStreamHandler(null)
        eventChannel = null
        context = null
    }
}
