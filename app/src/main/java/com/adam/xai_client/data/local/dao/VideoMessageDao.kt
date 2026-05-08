package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adam.xai_client.data.local.entity.VideoMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoMessageDao {
    @Query("SELECT * FROM video_messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(chatId: Long): Flow<List<VideoMessageEntity>>

    @Query("SELECT * FROM video_messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(chatId: Long): List<VideoMessageEntity>

    @Query("SELECT * FROM video_messages ORDER BY chatId ASC, createdAt ASC, id ASC")
    suspend fun getAllMessages(): List<VideoMessageEntity>

    @Query("SELECT * FROM video_messages WHERE id = :messageId")
    suspend fun getMessage(messageId: Long): VideoMessageEntity?

    @Query("SELECT * FROM video_messages WHERE parentMessageId = :parentMessageId ORDER BY createdAt ASC, id ASC")
    suspend fun getChildMessages(parentMessageId: Long): List<VideoMessageEntity>

    @Insert
    suspend fun insertMessage(message: VideoMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<VideoMessageEntity>)

    @Query("UPDATE video_messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    @Query("UPDATE video_messages SET activeChildMessageId = :activeChildMessageId WHERE id = :messageId")
    suspend fun updateActiveChild(messageId: Long, activeChildMessageId: Long?)

    @Query("UPDATE video_messages SET parentMessageId = :parentMessageId WHERE parentMessageId = :oldParentMessageId")
    suspend fun updateParentForChildren(oldParentMessageId: Long, parentMessageId: Long?)

    @Query("DELETE FROM video_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM video_messages")
    suspend fun deleteAllMessages()
}
