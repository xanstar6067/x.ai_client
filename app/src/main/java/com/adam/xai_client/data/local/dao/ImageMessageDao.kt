package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adam.xai_client.data.local.entity.ImageMessageEntity
import com.adam.xai_client.data.local.entity.ImageMessageSummary
import com.adam.xai_client.data.local.entity.ImagePayload
import com.adam.xai_client.data.local.entity.LegacyImageRef
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageMessageDao {
    @Query(
        """
        SELECT id, chatId, role, content, imageFilePath, imageMimeType,
            imageBytes IS NOT NULL OR imageFilePath IS NOT NULL AS hasImage,
            sourceMessageId, parentMessageId, activeChildMessageId, createdAt
        FROM image_messages
        WHERE chatId = :chatId
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeMessages(chatId: Long): Flow<List<ImageMessageSummary>>

    @Query(
        """
        SELECT id, chatId, role, content, imageFilePath, imageMimeType,
            imageBytes IS NOT NULL OR imageFilePath IS NOT NULL AS hasImage,
            sourceMessageId, parentMessageId, activeChildMessageId, createdAt
        FROM image_messages
        WHERE chatId = :chatId
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getMessages(chatId: Long): List<ImageMessageSummary>

    @Query("SELECT * FROM image_messages ORDER BY chatId ASC, createdAt ASC, id ASC")
    suspend fun getAllMessages(): List<ImageMessageEntity>

    @Query("SELECT * FROM image_messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessageEntities(chatId: Long): List<ImageMessageEntity>

    @Query(
        """
        SELECT id, chatId, role, content, imageFilePath, imageMimeType,
            imageBytes IS NOT NULL OR imageFilePath IS NOT NULL AS hasImage,
            sourceMessageId, parentMessageId, activeChildMessageId, createdAt
        FROM image_messages
        WHERE id = :messageId
        """
    )
    suspend fun getMessage(messageId: Long): ImageMessageSummary?

    @Query(
        """
        SELECT id, chatId, role, content, imageFilePath, imageMimeType,
            imageBytes IS NOT NULL OR imageFilePath IS NOT NULL AS hasImage,
            sourceMessageId, parentMessageId, activeChildMessageId, createdAt
        FROM image_messages
        WHERE parentMessageId = :parentMessageId
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getChildMessages(parentMessageId: Long): List<ImageMessageSummary>

    @Query("SELECT imageBytes, imageFilePath, imageMimeType FROM image_messages WHERE id = :messageId")
    suspend fun getImagePayload(messageId: Long): ImagePayload?

    @Query(
        """
        SELECT id, imageMimeType, length(imageBytes) AS byteCount
        FROM image_messages
        WHERE imageBytes IS NOT NULL AND imageFilePath IS NULL
        """
    )
    suspend fun getLegacyImageRefs(): List<LegacyImageRef>

    @Query("SELECT substr(imageBytes, :start, :length) FROM image_messages WHERE id = :messageId")
    suspend fun getImageBytesChunk(messageId: Long, start: Int, length: Int): ByteArray?

    @Query("UPDATE image_messages SET imageFilePath = :imageFilePath, imageBytes = NULL WHERE id = :messageId")
    suspend fun moveImageToFile(messageId: Long, imageFilePath: String)

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
