package cn.com.omnimind.baselib.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Embedded
import java.io.ByteArrayOutputStream

@Dao
interface AgentConversationEntryDao {
    @Query("DELETE FROM agent_conversation_entries WHERE conversationId = :conversationId AND conversationMode IN (:modes) AND entryId IN (:entryIds)")
    suspend fun deleteMessageEntries(conversationId: Long, modes: List<String>, entryIds: List<String>): Int

    @Query("""
        SELECT id, conversationId, conversationMode, entryId, entryType, status,
               '' AS summary, createdAt, updatedAt
        FROM agent_conversation_entries
        WHERE conversationId = :conversationId AND id > :afterEntryId
          AND entryType = 'tool_event'
        ORDER BY id ASC
    """)
    fun observeToolHeadersAfter(conversationId: Long, afterEntryId: Long):
        kotlinx.coroutines.flow.Flow<List<AgentConversationEntryHeader>>

    companion object {
        const val CHUNKED_ENTRY_PROJECTION = """id, conversationId, conversationMode, entryId, entryType, status,
            CASE WHEN length(CAST(summary AS BLOB)) > 32768 THEN '' ELSE summary END AS summary,
            CASE WHEN length(CAST(payloadJson AS BLOB)) > 32768 THEN '' ELSE payloadJson END AS payloadJson,
            createdAt, updatedAt,
            length(CAST(summary AS BLOB)) AS summaryBytes,
            length(CAST(payloadJson AS BLOB)) AS payloadBytes"""
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AgentConversationEntry): Long

    /**
     * 按消息正文搜索会话 id（正文存在 payloadJson 里）。
     *
     * 只回 conversationId（按会话分组去重、按最近更新时间排序），
     * 由调用方再按 id 取会话——避免把整段消息搬进内存/跨线程。
     */
    @Query(
        "SELECT conversationId FROM agent_conversation_entries " +
            "WHERE entryType != 'stream_event' AND payloadJson LIKE :pattern ESCAPE '\\' " +
            "GROUP BY conversationId ORDER BY MAX(updatedAt) DESC LIMIT :limit"
    )
    suspend fun searchConversationIdsByBody(pattern: String, limit: Int): List<Long>

    @Query(
        """
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND conversationMode = :conversationMode
          AND entryType != 'stream_event'
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getThreadEntriesAscSlices(
        conversationId: Long,
        conversationMode: String
    ): List<AgentConversationEntrySlice>

    @Query(
        """
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND conversationMode = :conversationMode
          AND entryType != 'stream_event'
        ORDER BY createdAt DESC, id DESC
        """
    )
    suspend fun getThreadEntriesDescSlices(
        conversationId: Long,
        conversationMode: String
    ): List<AgentConversationEntrySlice>

    @Query(
        """
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND entryType != 'stream_event'
        ORDER BY createdAt DESC, id DESC
        """
    )
    suspend fun getConversationEntriesDescSlices(conversationId: Long): List<AgentConversationEntrySlice>

    @Query(
        """
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND entryType != 'stream_event'
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getConversationEntriesAscSlices(conversationId: Long): List<AgentConversationEntrySlice>

    @Query(
        """
        SELECT
            id,
            conversationId,
            conversationMode,
            entryId,
            entryType,
            status,
            CASE
                WHEN LENGTH(summary) > 2048 THEN substr(summary, 1, 2048)
                ELSE summary
            END AS summary,
            createdAt,
            updatedAt
        FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND entryType != 'stream_event'
        ORDER BY createdAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestConversationEntryHeader(conversationId: Long): AgentConversationEntryHeader?

    @Query(
        """
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND entryType != 'stream_event'
        ORDER BY createdAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestConversationEntrySlices(conversationId: Long): AgentConversationEntrySlice?

    @Query(
        """
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND entryType != 'stream_event'
        ORDER BY createdAt ASC, id ASC
        LIMIT 1
        """
    )
    suspend fun getEarliestConversationEntrySlices(conversationId: Long): AgentConversationEntrySlice?

    @Query(
        """
        SELECT
            id,
            conversationId,
            conversationMode,
            entryId,
            entryType,
            status,
            CASE
                WHEN LENGTH(summary) > 2048 THEN substr(summary, 1, 2048)
                ELSE summary
            END AS summary,
            createdAt,
            updatedAt
        FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND entryType != 'stream_event'
        ORDER BY createdAt ASC, id ASC
        LIMIT 1
        """
    )
    suspend fun getEarliestConversationEntryHeader(conversationId: Long): AgentConversationEntryHeader?

    @Query(
        """
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND entryType != 'stream_event'
        ORDER BY updatedAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestConversationUpdateSlices(conversationId: Long): AgentConversationEntrySlice?

    @Query(
        """
        SELECT
            id,
            conversationId,
            conversationMode,
            entryId,
            entryType,
            status,
            CASE
                WHEN LENGTH(summary) > 2048 THEN substr(summary, 1, 2048)
                ELSE summary
            END AS summary,
            createdAt,
            updatedAt
        FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND entryType != 'stream_event'
        ORDER BY updatedAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestConversationUpdateHeader(conversationId: Long): AgentConversationEntryHeader?

    @Query(
        """
        SELECT COUNT(*) FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND entryType != 'stream_event'
        """
    )
    suspend fun countConversationEntries(conversationId: Long): Int

    @Query(
        """
        SELECT DISTINCT conversationId FROM agent_conversation_entries
        WHERE entryType = 'stream_event'
        """
    )
    suspend fun getConversationIdsWithStreamEvents(): List<Long>

    @Query(
        """
        DELETE FROM agent_conversation_entries
        WHERE entryType = 'stream_event'
        """
    )
    suspend fun deleteStreamEvents(): Int

    @Query(
        """
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND conversationMode = :conversationMode
          AND entryId = :entryId
          AND entryType != 'stream_event'
        LIMIT 1
        """
    )
    suspend fun getByThreadAndEntryIdSlices(
        conversationId: Long,
        conversationMode: String,
        entryId: String
    ): AgentConversationEntrySlice?

    @Query(
        """
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND conversationMode = :conversationMode
          AND entryType != 'stream_event'
          AND id > :afterEntryId
          AND (:afterEntryId = 0 OR NOT EXISTS (
            SELECT 1 FROM agent_conversation_entries AS preferred
            WHERE preferred.conversationId = :conversationId
              AND preferred.entryId = agent_conversation_entries.entryId
              AND preferred.entryType != 'stream_event'
              AND ((:conversationMode = 'codex' AND preferred.conversationMode = 'agent')
                OR (:conversationMode = 'normal' AND preferred.conversationMode IN ('agent', 'codex')))
          ))
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getThreadEntriesDescPagedSlices(
        conversationId: Long,
        conversationMode: String,
        limit: Int,
        offset: Int,
        afterEntryId: Long = 0
    ): List<AgentConversationEntrySlice>

    @Query(
        """
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND conversationMode = :conversationMode
          AND entryType != 'stream_event'
          AND id > :afterEntryId
          AND (:afterEntryId = 0 OR NOT EXISTS (
            SELECT 1 FROM agent_conversation_entries AS preferred
            WHERE preferred.conversationId = :conversationId
              AND preferred.entryId = agent_conversation_entries.entryId
              AND preferred.entryType != 'stream_event'
              AND ((:conversationMode = 'codex' AND preferred.conversationMode = 'agent')
                OR (:conversationMode = 'normal' AND preferred.conversationMode IN ('agent', 'codex')))
          ))
          AND (:cursorId = 0
            OR createdAt < :cursorCreatedAt
            OR (createdAt = :cursorCreatedAt AND id < :cursorId))
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getThreadEntriesDescAfterCursorSlices(
        conversationId: Long,
        conversationMode: String,
        limit: Int,
        cursorCreatedAt: Long,
        cursorId: Long,
        afterEntryId: Long = 0
    ): List<AgentConversationEntrySlice>

    @Transaction
    suspend fun getThreadEntriesDescAfterCursor(
        conversationId: Long,
        conversationMode: String,
        limit: Int,
        cursorCreatedAt: Long,
        cursorId: Long,
        afterEntryId: Long = 0
    ): List<AgentConversationEntry> =
        getThreadEntriesDescAfterCursorSlices(
            conversationId, conversationMode, limit, cursorCreatedAt, cursorId, afterEntryId
        ).map { hydrate(it) }

    @Query(
        """
        SELECT COUNT(*) FROM agent_conversation_entries
        WHERE conversationId = :conversationId
          AND conversationMode = :conversationMode
          AND entryType != 'stream_event'
        """
    )
    suspend fun countThreadEntries(
        conversationId: Long,
        conversationMode: String
    ): Int

    @Query(
        """
        DELETE FROM agent_conversation_entries
        WHERE conversationId = :conversationId AND conversationMode = :conversationMode
        """
    )
    suspend fun deleteThreadEntries(
        conversationId: Long,
        conversationMode: String
    ): Int

    @Query(
        """
        DELETE FROM agent_conversation_entries
        WHERE conversationId = :conversationId
        """
    )
    suspend fun deleteConversationEntries(conversationId: Long): Int

    // Split large values before they enter CursorWindow. Reading fewer rows alone
    // cannot help a single oversized ACP item. Transactions keep chunks consistent.
    @Transaction
    suspend fun getThreadEntriesAsc(
        conversationId: Long,
        conversationMode: String
    ): List<AgentConversationEntry> =
        getThreadEntriesAscSlices(conversationId, conversationMode).map { hydrate(it) }

    @Transaction
    suspend fun getThreadEntriesDesc(
        conversationId: Long,
        conversationMode: String
    ): List<AgentConversationEntry> =
        getThreadEntriesDescSlices(conversationId, conversationMode).map { hydrate(it) }

    @Transaction
    suspend fun getConversationEntriesDesc(conversationId: Long): List<AgentConversationEntry> =
        getConversationEntriesDescSlices(conversationId).map { hydrate(it) }

    @Transaction
    suspend fun getConversationEntriesAsc(conversationId: Long): List<AgentConversationEntry> =
        getConversationEntriesAscSlices(conversationId).map { hydrate(it) }

    @Transaction
    suspend fun getLatestConversationEntry(conversationId: Long): AgentConversationEntry? =
        getLatestConversationEntrySlices(conversationId)?.let { hydrate(it) }

    @Transaction
    suspend fun getEarliestConversationEntry(conversationId: Long): AgentConversationEntry? =
        getEarliestConversationEntrySlices(conversationId)?.let { hydrate(it) }

    @Transaction
    suspend fun getLatestConversationUpdate(conversationId: Long): AgentConversationEntry? =
        getLatestConversationUpdateSlices(conversationId)?.let { hydrate(it) }

    @Transaction
    suspend fun getByThreadAndEntryId(
        conversationId: Long,
        conversationMode: String,
        entryId: String
    ): AgentConversationEntry? =
        getByThreadAndEntryIdSlices(conversationId, conversationMode, entryId)?.let { hydrate(it) }

    @Query("""
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId AND conversationMode IN (:modes)
          AND entryType != 'stream_event'
          AND NOT EXISTS (
            SELECT 1 FROM agent_conversation_entries AS preferred
            WHERE preferred.conversationId = :conversationId
              AND preferred.entryId = agent_conversation_entries.entryId
              AND preferred.conversationMode IN (:modes)
              AND preferred.entryType != 'stream_event'
              AND (CASE preferred.conversationMode WHEN 'agent' THEN 0 WHEN 'codex' THEN 1 ELSE 2 END)
                < (CASE agent_conversation_entries.conversationMode WHEN 'agent' THEN 0 WHEN 'codex' THEN 1 ELSE 2 END)
          )
        ORDER BY createdAt DESC, id DESC LIMIT :limit OFFSET :offset
    """)
    suspend fun getLogicalThreadPageSlices(conversationId: Long, modes: List<String>, limit: Int, offset: Int): List<AgentConversationEntrySlice>

    @Transaction
    suspend fun getLogicalThreadPage(conversationId: Long, modes: List<String>, limit: Int, offset: Int): List<AgentConversationEntry> =
        getLogicalThreadPageSlices(conversationId, modes, limit, offset).map { hydrate(it) }

    @Query("""
        SELECT $CHUNKED_ENTRY_PROJECTION FROM agent_conversation_entries
        WHERE conversationId = :conversationId AND conversationMode IN (:modes)
          AND entryType != 'stream_event'
          AND NOT EXISTS (
            SELECT 1 FROM agent_conversation_entries AS preferred
            WHERE preferred.conversationId = :conversationId
              AND preferred.entryId = agent_conversation_entries.entryId
              AND preferred.conversationMode IN (:modes)
              AND preferred.entryType != 'stream_event'
              AND (CASE preferred.conversationMode WHEN 'agent' THEN 0 WHEN 'codex' THEN 1 ELSE 2 END)
                < (CASE agent_conversation_entries.conversationMode WHEN 'agent' THEN 0 WHEN 'codex' THEN 1 ELSE 2 END)
          )
          AND (:cursorId = 0
            OR createdAt < :cursorCreatedAt
            OR (createdAt = :cursorCreatedAt AND id < :cursorId))
        ORDER BY createdAt DESC, id DESC LIMIT :limit
    """)
    suspend fun getLogicalThreadPageAfterCursorSlices(
        conversationId: Long,
        modes: List<String>,
        limit: Int,
        cursorCreatedAt: Long,
        cursorId: Long
    ): List<AgentConversationEntrySlice>

    @Transaction
    suspend fun getLogicalThreadPageAfterCursor(
        conversationId: Long,
        modes: List<String>,
        limit: Int,
        cursorCreatedAt: Long,
        cursorId: Long
    ): List<AgentConversationEntry> =
        getLogicalThreadPageAfterCursorSlices(conversationId, modes, limit, cursorCreatedAt, cursorId).map { hydrate(it) }

    @Transaction
    suspend fun getThreadEntriesDescPaged(
        conversationId: Long,
        conversationMode: String,
        limit: Int,
        offset: Int,
        afterEntryId: Long = 0
    ): List<AgentConversationEntry> =
        getThreadEntriesDescPagedSlices(conversationId, conversationMode, limit, offset, afterEntryId).map { hydrate(it) }

    @Query("SELECT substr(CASE WHEN :summary THEN CAST(summary AS BLOB) ELSE CAST(payloadJson AS BLOB) END, :offset, 32768) FROM agent_conversation_entries WHERE id = :id")
    suspend fun readEntryChunk(id: Long, summary: Boolean, offset: Long): ByteArray?

    /** 只取摘要前 2048 字符：超长 summary 在页面投影里被置空，展示需要一小段预览。 */
    @Query("SELECT substr(summary, 1, 2048) FROM agent_conversation_entries WHERE id = :id")
    suspend fun readSummaryPreview(id: Long): String?

    /**
     * 把一条完整正文流式写出：内联部分直接写，超过 32KB 的分段读（substr 1-based 字节）。
     * 调用方持有读事务时，长度与所有分段共享同一快照。
     */
    suspend fun copyEntryTextTo(
        slice: AgentConversationEntrySlice,
        summary: Boolean,
        output: java.io.OutputStream
    ) {
        val bytes = if (summary) slice.summaryBytes else slice.payloadBytes
        val inline = if (summary) slice.entry.summary else slice.entry.payloadJson
        if (bytes <= 32768L) {
            output.write(inline.toByteArray(Charsets.UTF_8))
            output.flush()
            return
        }
        var offset = 1L
        while (offset <= bytes) {
            val chunk = readEntryChunk(slice.entry.id, summary, offset) ?: break
            if (chunk.isEmpty()) break
            output.write(chunk)
            offset += chunk.size
        }
        output.flush()
    }

    /**
     * 读历史时把残留的 running 工具条目改判为 interrupted。
     * 只动 status/summary/updatedAt：整行 upsert 会把投影/截断后的 payload 写回去，
     * 直接毁掉完整的工具记录。expectedUpdatedAt 保证并发写入不被覆盖。
     */
    @Query(
        """UPDATE agent_conversation_entries SET status = :status,
        summary = CASE WHEN trim(summary) = '' THEN :summary ELSE summary END,
        updatedAt = :updatedAt WHERE id = :id AND entryType = 'tool_event' AND status = 'running'
        AND updatedAt = :expectedUpdatedAt"""
    )
    suspend fun updateInterruptedToolHeader(
        id: Long, expectedUpdatedAt: Long, status: String, summary: String, updatedAt: Long,
    ): Int

    suspend fun hydrate(slice: AgentConversationEntrySlice): AgentConversationEntry {
        suspend fun fullText(summary: Boolean, bytes: Long, inline: String): String {
            if (bytes <= 32768) return inline
            val output = ByteArrayOutputStream()
            var offset = 1L
            while (offset <= bytes) {
                val chunk = readEntryChunk(slice.entry.id, summary, offset)
                check(chunk != null && chunk.isNotEmpty()) { "Conversation entry disappeared while reading" }
                output.write(chunk)
                offset += chunk.size
            }
            check(output.size().toLong() == bytes) { "Conversation entry length changed while reading" }
            return output.toString("UTF-8")
        }
        return slice.entry.copy(
            summary = fullText(true, slice.summaryBytes, slice.entry.summary),
            payloadJson = fullText(false, slice.payloadBytes, slice.entry.payloadJson),
        )
    }
}

data class AgentConversationEntrySlice(
    @Embedded val entry: AgentConversationEntry,
    val summaryBytes: Long,
    val payloadBytes: Long,
)
