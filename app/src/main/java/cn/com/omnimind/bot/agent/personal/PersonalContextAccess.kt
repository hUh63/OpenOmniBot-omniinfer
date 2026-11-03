package cn.com.omnimind.bot.agent.personal

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat

internal data class ContextAccessSnapshot(
    val id: String,
    val status: String,
    val scope: List<String> = emptyList(),
    val reason: String? = null,
) {
    val granted get() = status == "granted" || status == "partial" || status == "disconnected"
    fun payload() = mapOf("id" to id, "status" to status, "scope" to scope, "reason" to reason)
}

internal class PersonalContextAccess(private val context: Context) {
    fun granted(permission: String): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun all(): List<ContextAccessSnapshot> = IDS.map { id ->
        try { snapshot(id) }
        catch (_: Exception) { ContextAccessSnapshot(id, "unavailable", reason = "status_unavailable") }
    }

    fun snapshot(id: String): ContextAccessSnapshot {
        require(id in IDS) { "Unknown personal context capability" }
        if (id == "environment") return ContextAccessSnapshot(id, "granted")
        if (id == "documents") {
            val roots = PersonalContextDocuments(context).grants()
            return ContextAccessSnapshot(id, if (roots.isEmpty()) "denied" else "granted", if (roots.isEmpty()) emptyList() else listOf("selected_documents"))
        }
        if (id == "notifications") {
            val allowed = context.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(ComponentName(context, PersonalNotificationListener::class.java)) == true
            return ContextAccessSnapshot(id, when {
                !allowed -> "denied"
                !PersonalNotificationListener.connected -> "disconnected"
                else -> "granted"
            }, reason = if (allowed) "history_retention_7_days" else null)
        }
        if (id == "app_usage") {
            val allowed = context.getSystemService(AppOpsManager::class.java)?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
            return ContextAccessSnapshot(id, if (allowed) "granted" else "denied")
        }
        val authority = when (id) {
            "calendar_read", "calendar_write" -> "com.android.calendar"
            "contacts_read", "contacts_write" -> "com.android.contacts"
            "sms" -> "sms"
            "call_log" -> "call_log"
            "media_visual", "media_audio" -> "media"
            else -> null
        }
        if (authority != null && context.packageManager.resolveContentProvider(authority, 0) == null) {
            return ContextAccessSnapshot(id, "unavailable", reason = "provider_unavailable")
        }
        if (id == "location") {
            return when {
                granted(Manifest.permission.ACCESS_FINE_LOCATION) -> ContextAccessSnapshot(id, "granted", listOf("precise"))
                granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> ContextAccessSnapshot(id, "partial", listOf("approximate"))
                else -> ContextAccessSnapshot(id, "denied")
            }
        }
        val permissions = permissions(id)
        val allowed = permissions.filter(::granted)
        if (id == "media_visual") {
            val full = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else permissions
            return ContextAccessSnapshot(id, when {
                full.all(::granted) -> "granted"
                allowed.isNotEmpty() -> "partial"
                else -> "denied"
            }, allowed)
        }
        if (allowed.size == permissions.size) return ContextAccessSnapshot(id, "granted", allowed)
        val blocked = permissions.any { permission ->
            context.packageManager.isPermissionRevokedByPolicy(permission, context.packageName)
        } || ((id == "sms" || id == "call_log") && restrictedPermissions()?.let { allowlist ->
            permissions.any { it !in allowlist }
        } == true)
        return ContextAccessSnapshot(id, if (blocked) "restricted" else "denied", allowed,
            if (blocked) "system_or_installer_restricted" else if (id == "sms" || id == "call_log") "installer_may_restrict_access" else null)
    }

    private fun restrictedPermissions(): Set<String>? = try {
        context.packageManager.getWhitelistedRestrictedPermissions(context.packageName,
            PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER or PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM or PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE)
    } catch (_: SecurityException) {
        // 某些 ROM 不向普通应用开放此检查，不能据此推断用户已拒绝或已授权。
        null
    }

    fun requireAccess(id: String) {
        val state = snapshot(id)
        if (!state.granted) throw PersonalContextException(
            if (state.status == "denied") "permission_required" else state.status,
            "Open Settings > App permissions > Personal context to enable $id.", id,
        )
    }

    companion object {
        val IDS = listOf("calendar_read", "calendar_write", "contacts_read", "contacts_write", "notifications", "app_usage", "location", "background_location", "media_visual", "media_audio", "documents", "sms", "call_log", "environment")

        fun permissions(id: String): List<String> = when (id) {
            "calendar_read" -> listOf(Manifest.permission.READ_CALENDAR)
            "calendar_write" -> listOf(Manifest.permission.WRITE_CALENDAR)
            "contacts_read" -> listOf(Manifest.permission.READ_CONTACTS)
            "contacts_write" -> listOf(Manifest.permission.WRITE_CONTACTS)
            "location" -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            "background_location" -> listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            "sms" -> listOf(Manifest.permission.READ_SMS)
            "call_log" -> listOf(Manifest.permission.READ_CALL_LOG)
            "media_audio" -> listOf(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            "media_visual" -> if (Build.VERSION.SDK_INT >= 33) buildList {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            } else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> emptyList()
        }
    }
}
