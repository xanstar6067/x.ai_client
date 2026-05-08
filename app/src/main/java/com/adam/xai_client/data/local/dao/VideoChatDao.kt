package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.adam.xai_client.data.local.entity.VideoChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoChatDao {
    @Query("SELECT * FROM video_chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<VideoChatEntity>>

    @Query("SELECT * FROM video_chats WHERE id = :chatId")
    suspend fun getChat(chatId: Long): VideoChatEntity?

    @Query("SELECT * FROM video_chats ORDER BY createdAt ASC, id ASC")
    suspend fun getAllChats(): List<VideoChatEntity>

    @Insert
    suspend fun insertChat(chat: VideoChatEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<VideoChatEntity>)

    @Update
    suspend fun updateChat(chat: VideoChatEntity)

    @Query("DELETE FROM video_chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: Long)

    @Query("DELETE FROM video_chats")
    suspend fun deleteAllChats()
}
