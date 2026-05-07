package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.adam.xai_client.data.local.entity.ImageMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageMessageDao {
    @Query("SELECT * FROM image_messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(chatId: Long): Flow<List<ImageMessageEntity>>

    @Query("SELECT * FROM image_messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(chatId: Long): List<ImageMessageEntity>

    @Query("SELECT * FROM image_messages WHERE id = :messageId")
    suspend fun getMessage(messageId: Long): ImageMessageEntity?

    @Insert
    suspend fun insertMessage(message: ImageMessageEntity): Long
}
