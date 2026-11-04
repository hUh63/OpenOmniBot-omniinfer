package cn.com.omnimind.bot.statistics

import cn.com.omnimind.baselib.database.DatabaseHelper
import cn.com.omnimind.baselib.database.TokenUsageBucket
import cn.com.omnimind.bot.webchat.ConversationDomainService
import cn.com.omnimind.bot.webchat.VisibleConversationActivity
import java.time.Instant
import java.time.ZoneId

private const val DAY_MS = 86_400_000L
private const val WEEK_MS = 7 * DAY_MS

/** The host owns queries/aggregation; Flutter receives bounded chart display data. */
internal class UsageStatisticsService(
    private val readActivity: suspend (Long, Long) -> List<VisibleConversationActivity>,
    private val readTokens: suspend (Long, Long) -> List<TokenUsageBucket>,
    private val timeZone: () -> ZoneId = ZoneId::systemDefault,
) {
    constructor(conversations: ConversationDomainService) : this(
        conversations::listConversationActivity,
        DatabaseHelper::getTokenUsageWeeklyBuckets,
    )

    suspend fun conversationSummary(startDateMs: Long, todayMs: Long): Map<String, Any?> {
        val zone = timeZone()
        val tomorrow = Instant.ofEpochMilli(todayMs).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = if (startDateMs > todayMs) emptyList() else readActivity(startDateMs, tomorrow)
        val summary = conversationActivitySummary(rows, startDateMs, todayMs, zone)
        val localSummary = if (rows.none { it.isNa }) summary else
            conversationActivitySummary(rows.filterNot { it.isNa }, startDateMs, todayMs, zone)
        return summary + mapOf(
            // The Flutter engine also fences replies queued across an account change.
            "localSummary" to localSummary,
        )
    }

    suspend fun tokenSummary(alignedStartMs: Long, weekStartsMs: List<Long>): Map<String, Any?> {
        require(weekStartsMs.withIndex().all { (index, value) ->
            value == Math.addExact(alignedStartMs, Math.multiplyExact(index.toLong(), WEEK_MS))
        }) { "Chart weeks must follow the supplied elapsed-time start" }
        val until = Math.addExact(alignedStartMs, Math.multiplyExact(weekStartsMs.size.toLong(), WEEK_MS))
        val buckets = if (weekStartsMs.isEmpty()) emptyList() else readTokens(alignedStartMs, until)
        return tokenUsageSummary(buckets, weekStartsMs)
    }
}

internal fun conversationActivitySummary(
    rows: List<VisibleConversationActivity>,
    startDateMs: Long,
    todayMs: Long,
    zone: ZoneId,
): Map<String, Any?> {
    val activity = linkedMapOf<String, Int>()
    var total = 0
    for (row in rows) {
        val day = Instant.ofEpochMilli(row.createdAt).atZone(zone).toLocalDate()
        val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
        if (midnight < startDateMs || midnight > todayMs) continue
        val key = day.toString()
        activity[key] = (activity[key] ?: 0) + 1
        total++
    }
    var streak = 0
    var checkDate = todayMs
    while ((activity[Instant.ofEpochMilli(checkDate).atZone(zone).toLocalDate().toString()] ?: 0) > 0) {
        streak++
        checkDate -= DAY_MS // Match Dart DateTime.subtract(Duration(days: 1)), including DST.
    }
    return mapOf("activityMap" to activity, "totalConversations" to total, "currentStreak" to streak)
}

internal fun tokenUsageSummary(
    buckets: List<TokenUsageBucket>,
    weekStartsMs: List<Long>,
): Map<String, Any?> {
    val modelTokens = List(weekStartsMs.size) { linkedMapOf<String, Long>() }
    val cached = LongArray(weekStartsMs.size)
    val totals = linkedMapOf<String, Long>()
    var totalCached = 0L
    for (bucket in buckets) {
        if (bucket.weekIndex !in weekStartsMs.indices) continue
        cached[bucket.weekIndex] += bucket.cachedTokens
        totalCached += bucket.cachedTokens
        if (bucket.tokens <= 0L) continue
        val id = normalizeTokenModelId(bucket.model)
        val week = modelTokens[bucket.weekIndex]
        week[id] = (week[id] ?: 0L) + bucket.tokens
        totals[id] = (totals[id] ?: 0L) + bucket.tokens
    }
    val order = totals.keys.sortedWith(compareByDescending<String> { totals[it] }.thenBy { it })
    return mapOf(
        "weeklyData" to weekStartsMs.mapIndexed { index, start ->
            mapOf("weekStartMs" to start, "modelTokens" to modelTokens[index], "cachedTokens" to cached[index])
        },
        "totalCached" to totalCached,
        "modelTotals" to totals,
        "modelOrder" to order,
    )
}

// Dart/ECMAScript RegExp whitespace includes NBSP/BOM; Kotlin's default Regex does not.
private const val DART_SPACE = "[\\t-\\r \\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]"
private val spaces = Regex("$DART_SPACE+")
private val providerSeparator = Regex("$DART_SPACE*(?:\\||::)$DART_SPACE*")
private val pathSeparator = Regex("[/\\\\]")
private fun String.dartTrim(): String = trim {
    it in '\t'..'\r' || it == ' ' || it == '\u0085' || it == '\u00a0' || it == '\u1680' ||
        it in '\u2000'..'\u200a' || it == '\u2028' || it == '\u2029' || it == '\u202f' ||
        it == '\u205f' || it == '\u3000' || it == '\ufeff'
}

internal fun normalizeTokenModelId(rawModel: String): String {
    var value = rawModel.dartTrim()
    if (value.isEmpty()) return "unknown"
    value = value.replace(spaces, " ")
    val prefix = value.split(providerSeparator).filter { it.dartTrim().isNotEmpty() }
    if (prefix.size > 1) value = prefix.last().dartTrim()
    val path = value.split(pathSeparator).filter { it.dartTrim().isNotEmpty() }
    if (path.size > 1) {
        value = path.last().dartTrim()
    } else {
        val colon = value.indexOf(':')
        if (colon > 0 && colon < value.length - 1) value = value.substring(colon + 1).dartTrim()
    }
    return value.ifEmpty { "unknown" }
}
