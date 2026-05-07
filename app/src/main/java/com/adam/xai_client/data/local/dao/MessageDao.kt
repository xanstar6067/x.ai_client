package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.adam.xai_client.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(chatId: Long): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long
}
