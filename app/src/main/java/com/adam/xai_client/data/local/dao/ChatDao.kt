package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.adam.xai_client.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun observeChat(chatId: Long): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChat(chatId: Long): ChatEntity?

    @Insert
    suspend fun insertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: Long)
}
