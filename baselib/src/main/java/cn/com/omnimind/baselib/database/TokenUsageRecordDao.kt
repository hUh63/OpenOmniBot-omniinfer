package cn.com.omnimind.baselib.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TokenUsageRecordDao {

    @Insert
    suspend fun insert(record: TokenUsageRecord): Long

    @Query("SELECT * FROM token_usage_records WHERE createdAt >= :since ORDER BY createdAt ASC")
    suspend fun getRecordsSince(since: Long): List<TokenUsageRecord>

    // 用量图只回「每模型每周一行」，不再把整张 token 表搬进内存。
    // Prompt 用量已经包含缓存读写；明细是回退口径，不是额外计费。
    @Query(
        """
        SELECT model, CAST((createdAt - :since) / 604800000 AS INTEGER) AS weekIndex,
            SUM(MAX(promptTokens, MAX(cachedTokens, 0) + MAX(cacheCreationTokens, 0), 0)
                + MAX(completionTokens, MAX(reasoningTokens, 0) + MAX(textTokens, 0), 0)) AS tokens,
            SUM(MAX(cachedTokens, 0)) AS cachedTokens
        FROM token_usage_records
        WHERE createdAt >= :since AND createdAt < :until
        GROUP BY model, weekIndex
    """
    )
    suspend fun getWeeklyBuckets(since: Long, until: Long): List<TokenUsageBucket>

    @Query("SELECT * FROM token_usage_records WHERE conversationId = :conversationId ORDER BY createdAt DESC")
    suspend fun getByConversationId(conversationId: Long): List<TokenUsageRecord>

    @Query("DELETE FROM token_usage_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** 用量图的一格：某模型在某周（相对 :since 的周序号）的 token 合计。 */
data class TokenUsageBucket(
    val model: String,
    val weekIndex: Int,
    val tokens: Long,
    val cachedTokens: Long,
)
