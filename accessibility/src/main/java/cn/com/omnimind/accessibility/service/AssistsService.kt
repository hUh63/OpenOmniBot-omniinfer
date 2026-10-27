package cn.com.omnimind.accessibility.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CopyOnWriteArraySet

open class AssistsService : AccessibilityService() {
    @Volatile
    private var connected: Boolean = false

    @Volatile
    var lastPackageName: String = ""
        private set

    @Volatile
    var lastActivityName: String = ""
        private set

    override fun onCreate() {
        super.onCreate()
        connected = false
        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        markConnected()
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        markConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 不能无条件 markConnected()：服务被系统重建时新旧实例会短暂并存，旧实例在
        // onDestroy 前再收到一条事件就会把静态 instance 抢回一个正在销毁的服务，
        // 之后 isReady() 假阳性、dispatchGesture/takeScreenshot 全部静默失败。
        if (instance == null) {
            // 之前被 onUnbind/onDestroy 清空，但本实例仍在收事件 → 重新登记。
            markConnected()
        } else if (instance !== this) {
            // 新实例已经接管，旧实例的事件一律丢弃，也不要改写静态 instance。
            return
        } else if (!connected) {
            connected = true
        }
        event.packageName?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let {
            lastPackageName = it
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.className?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let {
                lastActivityName = it
            }
        }
        listeners.forEach { listener -> runCatching { listener.onAccessibilityEvent(event) } }
    }

    override fun onInterrupt() {
        listeners.forEach { listener -> runCatching { listener.onInterrupt() } }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        if (instance === this) instance = null
        listeners.forEach { listener -> runCatching { listener.onUnbind() } }
        super.onUnbind(intent)
        return true
    }

    override fun onDestroy() {
        connected = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun hideKeyboard() {
        softKeyboardController.showMode = SHOW_MODE_HIDDEN
    }

    fun restoreKeyboard() {
        softKeyboardController.showMode = SHOW_MODE_AUTO
    }

    private fun markConnected() {
        connected = true
        instance = this
        listeners.forEach { listener -> runCatching { listener.onServiceConnected(this) } }
    }

    companion object {
        @Volatile
        var instance: AssistsService? = null
            private set

        private val listeners = CopyOnWriteArraySet<AssistsServiceListener>()

        fun readyInstance(): AssistsService? = instance?.takeIf { it.connected }

        fun isReady(): Boolean = readyInstance() != null

        fun addListener(listener: AssistsServiceListener) {
            listeners += listener
        }

        fun removeListener(listener: AssistsServiceListener) {
            listeners -= listener
        }
    }
}
