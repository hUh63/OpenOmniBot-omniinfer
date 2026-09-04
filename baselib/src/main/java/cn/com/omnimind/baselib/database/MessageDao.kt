package cn.com.omnimind.baselib.database

import androidx.room.*

@Dao
interface MessageDao {
    
    @Insert
    suspend fun insert(message: Message): Long
    
    @Update
    suspend fun update(message: Message)
    
    @Delete
    suspend fun delete(message: Message)
    
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): Message?
    
    @Query("SELECT * FROM messages ORDER BY createdAt DESC")
    suspend fun getAll(): List<Message>
    
    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT :pageSize OFFSET :offset")
    suspend fun getMessagesByPage(offset: Int, pageSize: Int): List<Message>
    
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int
    
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long): Int
    
    @Query("DELETE FROM messages")
    suspend fun deleteAll(): Int
}