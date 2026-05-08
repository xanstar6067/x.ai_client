package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adam.xai_client.data.local.entity.ImageMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageMessageDao {
    @Query("SELECT * FROM image_messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(chatId: Long): Flow<List<ImageMessageEntity>>

    @Query("SELECT * FROM image_messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(chatId: Long): List<ImageMessageEntity>

    @Query("SELECT * FROM image_messages ORDER BY chatId ASC, createdAt ASC, id ASC")
    suspend fun getAllMessages(): List<ImageMessageEntity>

    @Query("SELECT * FROM image_messages WHERE id = :messageId")
    suspend fun getMessage(messageId: Long): ImageMessageEntity?

    @Query("SELECT * FROM image_messages WHERE parentMessageId = :parentMessageId ORDER BY createdAt ASC, id ASC")
    suspend fun getChildMessages(parentMessageId: Long): List<ImageMessageEntity>

    @Insert
    suspend fun insertMessage(message: ImageMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ImageMessageEntity>)

    @Query("UPDATE image_messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    @Query("UPDATE image_messages SET activeChildMessageId = :activeChildMessageId WHERE id = :messageId")
    suspend fun updateActiveChild(messageId: Long, activeChildMessageId: Long?)

    @Query("UPDATE image_messages SET parentMessageId = :parentMessageId WHERE parentMessageId = :oldParentMessageId")
    suspend fun updateParentForChildren(oldParentMessageId: Long, parentMessageId: Long?)

    @Query("DELETE FROM image_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM image_messages")
    suspend fun deleteAllMessages()
}
