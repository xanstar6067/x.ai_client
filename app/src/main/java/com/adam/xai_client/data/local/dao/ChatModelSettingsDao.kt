package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adam.xai_client.data.local.entity.ChatModelSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatModelSettingsDao {
    @Query("SELECT * FROM chat_model_settings WHERE chatId = :chatId")
    fun observeSettings(chatId: Long): Flow<ChatModelSettingsEntity?>

    @Query("SELECT * FROM chat_model_settings WHERE chatId = :chatId")
    suspend fun getSettings(chatId: Long): ChatModelSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: ChatModelSettingsEntity)
}
