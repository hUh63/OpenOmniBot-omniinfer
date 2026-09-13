package cn.com.omnimind.bot.agent

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

internal object ConversationSnapshotOrdering {

    internal data class PreparedMessage(
        val payload: Map<String, Any?>,
        val createdAt: Long,
        val taskAnchor: Long,
        val taskKey: String,
        val streamSeq: Long?,
        val phaseRank: Int,
        val sequenceRank: Int,
        val originalIndex: Int
    )

    fun prepareForStorage(messages: List<Map<String, Any?>>): List<PreparedMessage> {
        val fallbackCreatedAt = System.currentTimeMillis()
        val prepared = messages.mapIndexed { index, message ->
            val createdAt = resolveCreatedAtMillis(message) ?: fallbackCreatedAt
            val taskId = resolveTaskId(message)
            val taskAnchor = resolveTaskAnchorMillis(message) ?: createdAt
            PreparedMessage(
                payload = message,
                createdAt = createdAt,
                taskAnchor = taskAnchor,
                taskKey = taskId ?: "anchor:$taskAnchor",
                streamSeq = resolveStreamSeq(message),
                phaseRank = resolvePhaseRank(message),
                sequenceRank = resolveSequenceRank(message),
                originalIndex = index
            )
        }
        val tasksRequiringCreatedAtOrdering = prepared.groupBy { it.taskKey }
            .filterValues { entries ->
                val seqs = entries.mapNotNull { it.streamSeq }
                seqs.size != entries.size || seqs.size != seqs.toSet().size
            }
            .keys
        return prepared.sortedWith { left, right ->
            comparePreparedMessages(left, right, tasksRequiringCreatedAtOrdering)
        }
    }

    fun sortForDisplay(messages: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return prepareForStorage(messages)
            .asReversed()
            .map { it.payload }
    }

    fun resolveCreatedAtMillis(message: Map<String, Any?>): Long? {
        return resolveDeepThinkingStartTime(message)
            ?: parseCreatedAtMillis(message["createAt"])
            ?: parseIdTimestamp(message["id"])
            ?: parseIdTimestamp(contentValue(message, "id"))
    }

    private fun contentValue(message: Map<String, Any?>, key: String): Any? {
        val content = message["content"] as? Map<*, *> ?: return null
        return content[key]
    }

    private fun resolveDeepThinkingStartTime(message: Map<String, Any?>): Long? {
        val cardData = cardData(message) ?: return null
        if (cardData["type"]?.toString() != "deep_thinking") {
            return null
        }
        return parseCreatedAtMillis(cardData["startTime"])
    }

    private fun cardData(message: Map<String, Any?>): Map<String, Any?>? {
        val content = message["content"] as? Map<*, *> ?: return null
        val cardData = content["cardData"] as? Map<*, *> ?: return null
        return cardData.entries.associate { (key, value) ->
            key.toString() to value
        }
    }

    private fun resolveTaskAnchorMillis(message: Map<String, Any?>): Long? {
        val taskId = resolveTaskId(message)
        return parseIdTimestamp(taskId)
            ?: parseIdTimestamp(message["id"])
            ?: parseIdTimestamp(contentValue(message, "id"))
    }

    private fun resolveTaskId(message: Map<String, Any?>): String? {
        val topLevelId = message["id"]?.toString()?.trim().orEmpty()
        val cardData = cardData(message)
        val cardTaskId = cardData?.get("taskID")?.toString()?.trim().orEmpty()
        if (cardTaskId.isNotEmpty()) {
            return cardTaskId
        }
        val toolTaskId = cardData?.get("taskId")?.toString()?.trim().orEmpty()
        if (toolTaskId.isNotEmpty()) {
            return toolTaskId
        }
        return when {
            topLevelId.endsWith("-user") -> topLevelId.removeSuffix("-user")
            topLevelId.endsWith("-assistant") -> topLevelId.removeSuffix("-assistant")
            topLevelId.endsWith("-clarify") -> topLevelId.removeSuffix("-clarify")
            topLevelId.endsWith("-permission") -> topLevelId.removeSuffix("-permission")
            topLevelId.endsWith("-thinking") -> topLevelId.removeSuffix("-thinking")
            topLevelId.contains("-thinking-") -> topLevelId.substringBefore("-thinking-")
            topLevelId.endsWith("-text") -> topLevelId.removeSuffix("-text")
            topLevelId.contains("-text-") -> topLevelId.substringBefore("-text-")
            topLevelId.contains("-tool-") -> topLevelId.substringBefore("-tool-")
            else -> topLevelId.ifEmpty { null }
        }
    }

    private fun resolveStreamSeq(message: Map<String, Any?>): Long? {
        val streamMeta = streamMeta(message) ?: return null
        return parseCreatedAtMillis(streamMeta["entrySeq"])
            ?: parseCreatedAtMillis(streamMeta["seq"])
    }

    private fun streamMeta(message: Map<String, Any?>): Map<String, Any?>? {
        val raw = message["streamMeta"] as? Map<*, *> ?: return null
        return raw.entries.associate { (key, value) ->
            key.toString() to value
        }
    }

    private fun resolvePhaseRank(message: Map<String, Any?>): Int {
        val type = (message["type"] as? Number)?.toInt()
        val user = (message["user"] as? Number)?.toInt()
        val cardType = cardData(message)?.get("type")?.toString().orEmpty()
        return when {
            type == 1 && user == 1 -> 0
            type == 2 && cardType == "deep_thinking" -> 1
            type == 1 && user == 2 -> 2
            type == 2 -> 3
            else -> 4
        }
    }

    private fun resolveTurnRank(message: Map<String, Any?>): Int {
        val type = (message["type"] as? Number)?.toInt()
        val user = (message["user"] as? Number)?.toInt()
        return if (type == 1 && user == 1) 0 else 1
    }

    private fun resolveSequenceRank(message: Map<String, Any?>): Int {
        val id = message["id"]?.toString()?.trim().orEmpty()
        return when {
            id.contains("-thinking-") -> id.substringAfterLast("-thinking-").toIntOrNull() ?: 1
            id.endsWith("-thinking") -> 1
            id.contains("-text-") -> id.substringAfterLast("-text-").toIntOrNull() ?: 1
            id.endsWith("-text") -> 1
            id.contains("-tool-") -> id.substringAfterLast("-tool-").toIntOrNull() ?: 1
            else -> 0
        }
    }

    private fun parseCreatedAtMillis(raw: Any?): Long? {
        return when (raw) {
            null -> null
            is Number -> raw.toLong()
            is String -> parseCreatedAtString(raw)
            else -> parseCreatedAtString(raw.toString())
        }
    }

    private fun parseCreatedAtString(raw: String): Long? {
        val normalized = raw.trim()
        if (normalized.isEmpty()) {
            return null
        }

        normalized.toLongOrNull()?.let { return it }

        runCatching { Instant.parse(normalized).toEpochMilli() }.getOrNull()?.let {
            return it
        }
        runCatching { OffsetDateTime.parse(normalized).toInstant().toEpochMilli() }.getOrNull()
            ?.let {
                return it
            }
        runCatching {
            LocalDateTime.parse(normalized).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()?.let {
            return it
        }

        return null
    }

    private fun parseIdTimestamp(raw: Any?): Long? {
        val normalized = raw?.toString()?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return null
        }
        // Only the legacy ids generated by OmniBot encode a timestamp. ACP
        // message ids are opaque strings and may legally begin with a long run
        // of digits (Claude currently emits values such as `021785489...`).
        // Treating any numeric prefix as an epoch moves those messages far into
        // the future and groups every user prompt ahead of every agent reply.
        return LEGACY_TIMESTAMP_ID.matchEntire(normalized)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
    }

    private val LEGACY_TIMESTAMP_ID = Regex(
        "^(\\d{13})-(?:user|ai(?:-.+)?|assistant(?:-.+)?|clarify(?:-.+)?|" +
            "permission(?:-.+)?|thinking(?:-.+)?|text(?:-.+)?|tool(?:-.+)?)$"
    )

    private fun comparePreparedMessages(
        left: PreparedMessage,
        right: PreparedMessage,
        tasksRequiringCreatedAtOrdering: Set<String>
    ): Int {
        compareValues(left.taskAnchor, right.taskAnchor).takeIf { it != 0 }?.let {
            return it
        }
        compareValues(resolveTurnRank(left.payload), resolveTurnRank(right.payload))
            .takeIf { it != 0 }
            ?.let { return it }

        val sameFallbackTask = left.taskKey == right.taskKey &&
            tasksRequiringCreatedAtOrdering.contains(left.taskKey)
        if (sameFallbackTask) {
            compareValues(left.createdAt, right.createdAt).takeIf { it != 0 }?.let {
                return it
            }
            compareValues(left.streamSeq ?: Long.MAX_VALUE, right.streamSeq ?: Long.MAX_VALUE)
                .takeIf { it != 0 }
                ?.let { return it }
        } else {
            compareValues(left.streamSeq ?: Long.MAX_VALUE, right.streamSeq ?: Long.MAX_VALUE)
                .takeIf { it != 0 }
                ?.let { return it }
            compareValues(left.createdAt, right.createdAt).takeIf { it != 0 }?.let {
                return it
            }
        }

        compareValues(left.phaseRank, right.phaseRank).takeIf { it != 0 }?.let {
            return it
        }
        compareValues(left.sequenceRank, right.sequenceRank).takeIf { it != 0 }?.let {
            return it
        }
        return -compareValues(left.originalIndex, right.originalIndex)
    }
}
