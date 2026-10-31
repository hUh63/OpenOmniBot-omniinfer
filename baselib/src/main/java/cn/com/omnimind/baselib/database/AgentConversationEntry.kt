package cn.com.omnimind.baselib.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "agent_conversation_entries",
    indices = [
        Index(
            value = ["conversationId", "conversationMode", "entryId"],
            unique = true
        ),
        Index(value = ["conversationId", "conversationMode", "updatedAt"]),
        // 历史读取是 WHERE conversationId + conversationMode ORDER BY createdAt DESC, id DESC，
        // 没有这个索引时每翻一页都要把整段条目排序一次。
        Index(value = ["conversationId", "conversationMode", "createdAt"]),
        // 旧 stream_event 的普查与清理按 entryType 过滤，之前是全表扫描。
        Index(value = ["entryType"])
    ]
)
data class AgentConversationEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val conversationMode: String,
    val entryId: String,
    val entryType: String,
    val status: String,
    val summary: String,
    val payloadJson: String,
    val createdAt: Long = Date().time,
    val updatedAt: Long = Date().time
)
