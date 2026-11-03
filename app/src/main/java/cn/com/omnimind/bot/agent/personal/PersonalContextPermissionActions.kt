package cn.com.omnimind.bot.agent.personal

import android.content.Context
import android.content.Intent
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*

internal class PersonalContextPermissionActions(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun handle(call: MethodCall, result: MethodChannel.Result): Boolean {
        if (call.method !in METHODS) return false
        scope.launch {
            try {
                when (call.method) {
                    "getPersonalContextCapabilities" -> result.success(withContext(Dispatchers.IO) { PersonalContextAccess(context).all().map { it.payload() } })
                    "requestPersonalContextAccess" -> {
                        val id = call.argument<String>("capabilityId").orEmpty()
                        require(id in PersonalContextAccess.IDS) { "Unknown capability" }
                        if (id == "background_location" && !PersonalContextAccess(context).snapshot("location").granted) {
                            result.error("foreground_permission_required", "Enable foreground location first.", null)
                        } else {
                            context.startActivity(Intent(context, PersonalContextAccessActivity::class.java)
                                .putExtra("capability", id).putExtra("directory", call.argument<Boolean>("directory") == true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            result.success(true)
                        }
                    }
                    "clearPersonalNotificationHistory" -> {
                        withContext(Dispatchers.IO) { PersonalNotificationHistory.get(context).clear() }
                        result.success(true)
                    }
                    "revokePersonalDocumentAccess" -> {
                        withContext(Dispatchers.IO) { PersonalContextDocuments(context).revokeAll() }
                        result.success(true)
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { result.error("personal_context_unavailable", "Unable to complete this access operation.", null) }
        }
        return true
    }

    fun close() { scope.cancel() }
    companion object {
        private val METHODS = setOf("getPersonalContextCapabilities", "requestPersonalContextAccess", "clearPersonalNotificationHistory", "revokePersonalDocumentAccess")
    }
}
