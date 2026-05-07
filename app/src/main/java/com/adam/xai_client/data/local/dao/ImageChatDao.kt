package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.adam.xai_client.data.local.entity.ImageChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageChatDao {
    @Query("SELECT * FROM image_chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<ImageChatEntity>>

    @Query("SELECT * FROM image_chats WHERE id = :chatId")
    suspend fun getChat(chatId: Long): ImageChatEntity?

    @Query("SELECT * FROM image_chats ORDER BY createdAt ASC, id ASC")
    suspend fun getAllChats(): List<ImageChatEntity>

    @Insert
    suspend fun insertChat(chat: ImageChatEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ImageChatEntity>)

    @Update
    suspend fun updateChat(chat: ImageChatEntity)

    @Query("DELETE FROM image_chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: Long)

    @Query("DELETE FROM image_chats")
    suspend fun deleteAllChats()
}
