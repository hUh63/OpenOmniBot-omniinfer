package cn.com.omnimind.bot.agent.personal

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class PersonalNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val records = Channel<ContextNotification>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (record in records) {
                if (PersonalContextAccess(this@PersonalNotificationListener).snapshot("notifications").granted) {
                    try {
                        PersonalNotificationHistory.get(this@PersonalNotificationListener).record(record)
                        historyAvailable = true
                    } catch (_: android.database.SQLException) {
                        historyAvailable = false
                    }
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        service = this
        try { activeNotifications?.forEach { records.trySend(ContextNotification.from(it)) } }
        catch (_: SecurityException) { service = null }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) { records.trySend(ContextNotification.from(sbn)) }
    override fun onListenerDisconnected() {
        if (service === this) service = null
        super.onListenerDisconnected()
    }
    override fun onDestroy() {
        if (service === this) service = null
        records.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile private var service: PersonalNotificationListener? = null
        @Volatile internal var historyAvailable: Boolean = true
            private set
        internal val connected get() = service != null
        internal fun current(): List<ContextNotification> {
            val listener = service ?: throw PersonalContextException("service_disconnected", "Notification access is enabled, but the listener is not connected.", "notifications")
            return listener.activeNotifications?.map(ContextNotification::from)
                ?: throw PersonalContextException("service_disconnected", "The notification service did not return current notifications.", "notifications")
        }
    }
}
