package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adam.xai_client.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessage(messageId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE parentMessageId = :parentMessageId ORDER BY createdAt ASC, id ASC")
    suspend fun getChildMessages(parentMessageId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY chatId ASC, createdAt ASC, id ASC")
    suspend fun getAllMessages(): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET content = :content, reasoningContent = :reasoningContent, responseId = COALESCE(:responseId, responseId), tokenCount = :tokenCount WHERE id = :messageId")
    suspend fun updateMessageContent(
        messageId: Long,
        content: String,
        reasoningContent: String?,
        responseId: String?,
        tokenCount: Int?
    )

    @Query("UPDATE messages SET responseId = NULL WHERE id = :messageId")
    suspend fun clearResponseId(messageId: Long)

    @Query("UPDATE messages SET activeChildMessageId = :activeChildMessageId WHERE id = :messageId")
    suspend fun updateActiveChild(messageId: Long, activeChildMessageId: Long?)

    @Query("UPDATE messages SET parentMessageId = :parentMessageId WHERE parentMessageId = :oldParentMessageId")
    suspend fun updateParentForChildren(oldParentMessageId: Long, parentMessageId: Long?)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM messages WHERE id IN (:messageIds)")
    suspend fun deleteMessagesByIds(messageIds: List<Long>)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
