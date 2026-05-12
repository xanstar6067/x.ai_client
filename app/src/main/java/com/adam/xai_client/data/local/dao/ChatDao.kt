package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query("SELECT * FROM chats ORDER BY createdAt ASC, id ASC")
    suspend fun getAllChats(): List<ChatEntity>

    @Insert
    suspend fun insertChat(chat: ChatEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query(
        """
        UPDATE chats
        SET cachedTokenCount = cachedTokenCount + :cachedTokens,
            totalPromptTokenCount = totalPromptTokenCount + :promptTokens,
            totalCompletionTokenCount = totalCompletionTokenCount + :completionTokens,
            totalImageTokenCount = totalImageTokenCount + :imageTokens,
            totalReasoningTokenCount = totalReasoningTokenCount + :reasoningTokens,
            lastPromptTokenCount = :promptTokens,
            lastCompletionTokenCount = :completionTokens,
            lastCachedTokenCount = :cachedTokens,
            lastReasoningTokenCount = :reasoningTokens,
            accumulatedCostMicros = accumulatedCostMicros + :costMicros,
            lastRequestCostMicros = :costMicros
        WHERE id = :chatId
        """
    )
    suspend fun updateTokenUsage(
        chatId: Long,
        promptTokens: Int,
        completionTokens: Int,
        cachedTokens: Int,
        reasoningTokens: Int,
        imageTokens: Int,
        costMicros: Long
    )

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: Long)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()
}
