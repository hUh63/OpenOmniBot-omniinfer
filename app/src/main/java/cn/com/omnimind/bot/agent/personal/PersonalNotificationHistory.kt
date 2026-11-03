package cn.com.omnimind.bot.agent.personal

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.service.notification.StatusBarNotification
import kotlinx.serialization.json.JsonObject

internal data class ContextNotification(
    val key: String, val packageName: String, val title: String?, val text: String?,
    val postedAt: Long, val capturedAt: Long, val contentTruncated: Boolean,
) {
    fun payload() = mapOf("key" to key, "packageName" to packageName, "title" to title, "text" to text,
        "postedAt" to postedAt, "capturedAt" to capturedAt, "contentTruncated" to contentTruncated)
    companion object {
        fun from(notification: StatusBarNotification): ContextNotification {
            val extras = notification.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
                ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            return ContextNotification(notification.key, notification.packageName, title?.take(4000), text?.take(8000), notification.postTime,
                System.currentTimeMillis(), (title?.length ?: 0) > 4000 || (text?.length ?: 0) > 8000)
        }
    }
}

internal class PersonalNotificationHistory private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE notifications (notification_key TEXT PRIMARY KEY, package_name TEXT NOT NULL, title TEXT, body TEXT, posted_at INTEGER NOT NULL, captured_at INTEGER NOT NULL, truncated INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX notifications_posted ON notifications(posted_at DESC, notification_key)")
        db.execSQL("CREATE TABLE history_settings (id INTEGER PRIMARY KEY, cleared_before INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun record(notification: ContextNotification, now: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            prune(db, now)
            val clearedBefore = db.rawQuery("SELECT cleared_before FROM history_settings WHERE id=1", null).use { if (it.moveToFirst()) it.getLong(0) else Long.MIN_VALUE }
            if (notification.postedAt >= cutoff(now) && notification.capturedAt > clearedBefore) {
                db.insertWithOnConflict("notifications", null, ContentValues().apply {
                    put("notification_key", notification.key); put("package_name", notification.packageName)
                    put("title", notification.title); put("body", notification.text)
                    put("posted_at", notification.postedAt); put("captured_at", notification.capturedAt)
                    put("truncated", if (notification.contentTruncated) 1 else 0)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.execSQL("DELETE FROM notifications WHERE notification_key IN (SELECT notification_key FROM notifications ORDER BY posted_at DESC, notification_key LIMIT -1 OFFSET 10000)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun query(args: JsonObject, now: Long = System.currentTimeMillis()): Map<String, Any?> {
        val page = ContextPage.from(args)
        val requested = ContextTimeRange.from(args, now, 7)
        val start = maxOf(requested.start, cutoff(now))
        val clauses = mutableListOf("posted_at>=?", "posted_at<?")
        val values = mutableListOf(start.toString(), requested.end.toString())
        args.text("packageName")?.takeIf { it.isNotBlank() }?.let { clauses += "package_name=?"; values += it }
        args.text("query")?.takeIf { it.isNotBlank() }?.let {
            clauses += "(title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\')"
            values += likePattern(it); values += likePattern(it)
        }
        val db = writableDatabase
        prune(db, now)
        val rows = mutableListOf<Map<String, Any?>>()
        db.query("notifications", null, clauses.joinToString(" AND "), values.toTypedArray(), null, null,
            "posted_at DESC, notification_key", "${page.offset},${page.limit + 1}").use { cursor ->
            while (cursor.moveToNext()) rows += mapOf("key" to cursor.value("notification_key"), "packageName" to cursor.value("package_name"),
                "title" to cursor.value("title"), "text" to cursor.value("body"), "postedAt" to cursor.longValue("posted_at"),
                "capturedAt" to cursor.longValue("captured_at"), "contentTruncated" to (cursor.longValue("truncated") == 1L))
        }
        return page.result("notification_history", rows, mapOf("retentionDays" to 7, "maxStoredRecords" to 10000,
            "requestedRange" to requested.payload(), "availableFrom" to java.time.Instant.ofEpochMilli(start).toString(),
            "scope" to "notifications_observed_while_authorized"))
    }

    @Synchronized fun clear(now: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("notifications", null, null)
            db.execSQL("INSERT OR REPLACE INTO history_settings(id,cleared_before) VALUES(1,?)", arrayOf(now))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun prune(db: SQLiteDatabase, now: Long) { db.delete("notifications", "posted_at<?", arrayOf(cutoff(now).toString())) }

    companion object {
        const val DATABASE_NAME = "personal_context_notifications.db"
        internal fun cutoff(now: Long): Long = now - 7L * 86_400_000
        @Volatile private var instance: PersonalNotificationHistory? = null
        fun get(context: Context): PersonalNotificationHistory = instance ?: synchronized(this) {
            instance ?: PersonalNotificationHistory(context.applicationContext).also { instance = it }
        }
    }
}
