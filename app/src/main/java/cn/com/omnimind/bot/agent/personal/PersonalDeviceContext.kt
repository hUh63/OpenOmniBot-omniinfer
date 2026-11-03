package cn.com.omnimind.bot.agent.personal

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import androidx.core.location.LocationManagerCompat
import cn.com.omnimind.bot.util.NestedBackgroundStateUtil
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.Executor
import kotlin.coroutines.resume

internal class PersonalDeviceContext(private val context: Context) {
    private val access get() = PersonalContextAccess(context)

    suspend fun usage(args: JsonObject): Map<String, Any?> {
        access.requireAccess("app_usage")
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) throw PersonalContextException("device_locked", "Unlock the device to query app usage.", "app_usage")
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: throw PersonalContextException("unavailable", "App usage service is unavailable.")
        val page = ContextPage.from(args)
        val range = ContextTimeRange.from(args)
        require(range.end - range.start <= 366L * 86_400_000) { "Query at most one year at a time" }
        val packageName = args.text("packageName").orEmpty()
        val mode = args.text("mode") ?: "summary"
        require(mode in setOf("summary", "recent")) { "mode must be summary or recent" }
        val rows: List<Map<String, Any?>> = if (mode == "recent") {
            val events = manager.queryEvents(range.start, range.end) ?: throw PersonalContextException("provider_unavailable", "The system did not return usage events.")
            val retained = ArrayDeque<Map<String, Any?>>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                currentCoroutineContext().ensureActive()
                events.getNextEvent(event)
                if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED || (packageName.isNotBlank() && packageName != event.packageName)) continue
                retained.addFirst(mapOf("packageName" to event.packageName, "activity" to event.className, "timestamp" to event.timeStamp))
                if (retained.size > page.offset + page.limit + 1) retained.removeLast()
            }
            retained.drop(page.offset)
        } else {
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, range.start, range.end)
                ?: throw PersonalContextException("provider_unavailable", "The system did not return usage statistics.")
            stats.filter { (packageName.isBlank() || it.packageName == packageName) && it.totalTimeInForeground > 0 }
                .groupBy { it.packageName }.map { (pkg, entries) ->
                    mapOf("packageName" to pkg, "foregroundMillis" to entries.sumOf { it.totalTimeInForeground }, "lastUsedAt" to entries.maxOf { it.lastTimeUsed },
                        "bucketStartAt" to entries.minOf { it.firstTimeStamp }, "bucketEndAt" to entries.maxOf { it.lastTimeStamp })
                }.sortedByDescending { it["foregroundMillis"] as Long }.drop(page.offset).take(page.limit + 1)
        }
        access.requireAccess("app_usage")
        return page.result("app_usage", rows, mapOf("mode" to mode, "requestedRange" to range.payload(),
            "scope" to if (mode == "summary") "system_daily_buckets_may_extend_requested_range" else "system_retained_activity_events"))
    }

    @android.annotation.SuppressLint("MissingPermission")
    suspend fun location(): Map<String, Any?> {
        requireLocationAccess()
        val manager = context.getSystemService(LocationManager::class.java) ?: throw PersonalContextException("unavailable", "Location service is unavailable.", "location")
        if (!manager.isLocationEnabled) throw PersonalContextException("location_disabled", "Enable system location services.", "location")
        val providers = manager.getProviders(true).filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
        if (providers.isEmpty()) throw PersonalContextException("unavailable", "No location provider is enabled.", "location")
        val cached = providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }
        val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER } ?: providers.first()
        val fresh = withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellation = android.os.CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, Executor { it.run() }) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            }
        }
        requireLocationAccess()
        val location = fresh ?: cached ?: throw PersonalContextException("location_unavailable", "No location fix was available within 10 seconds.", "location")
        val age = ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000).coerceAtLeast(0)
        return mapOf("success" to true, "source" to "location", "latitude" to location.latitude, "longitude" to location.longitude,
            "accuracyMeters" to if (location.hasAccuracy()) location.accuracy else null, "sampledAt" to location.time,
            "ageMillis" to age, "cached" to (fresh == null), "stale" to (age > 120_000),
            "freshFixUnavailable" to (fresh == null), "scope" to access.snapshot("location").scope)
    }

    private fun requireLocationAccess() {
        access.requireAccess("location")
        if (!NestedBackgroundStateUtil.getInstance().hasVisibleActivity() && !access.granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            throw PersonalContextException("background_permission_required", "Return to OmniBot or enable background location in Personal context settings.", "background_location")
        }
    }

    fun environment(): Map<String, Any?> {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        val audio = context.getSystemService(AudioManager::class.java)
        return mapOf("success" to true, "source" to "device_environment", "sampledAt" to System.currentTimeMillis(),
            "batteryPercent" to if (level >= 0 && scale > 0) level * 100.0 / scale else null,
            "plugged" to battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            "batteryStatus" to battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            "interactive" to context.getSystemService(PowerManager::class.java)?.isInteractive,
            "deviceLocked" to context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked,
            "powerSaveMode" to context.getSystemService(PowerManager::class.java)?.isPowerSaveMode,
            "networkTypes" to listOf(NetworkCapabilities.TRANSPORT_WIFI to "wifi", NetworkCapabilities.TRANSPORT_CELLULAR to "cellular", NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet", NetworkCapabilities.TRANSPORT_VPN to "vpn").filter { network?.hasTransport(it.first) == true }.map { it.second },
            "internetValidated" to network?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            "ringerMode" to audio?.ringerMode, "interruptionFilter" to context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter,
            "audioOutputs" to audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.map { mapOf("type" to it.type) }.orEmpty())
    }
}
