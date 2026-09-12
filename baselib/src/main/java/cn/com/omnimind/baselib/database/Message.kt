package cn.com.omnimind.baselib.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String ,//消息ID
    val type: Int,//1普通消息 2卡片消息
    val user: Int,//1用户 2机器人 3系统
    val content: String,
    val createdAt: Long = Date().time,
    val updatedAt: Long = Date().time,
)

data class PagedMessagesResult(
    val messageList: List<Message>,
    val hasMore: Boolean
)