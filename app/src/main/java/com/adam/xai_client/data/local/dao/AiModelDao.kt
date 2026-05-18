package com.adam.xai_client.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adam.xai_client.data.local.entity.AiModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiModelDao {
    @Query("SELECT * FROM ai_models ORDER BY id COLLATE NOCASE ASC")
    fun observeModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE isEnabledForChat = 1 ORDER BY id COLLATE NOCASE ASC")
    fun observeEnabledModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models ORDER BY id COLLATE NOCASE ASC")
    suspend fun getModels(): List<AiModelEntity>

    @Query("SELECT * FROM ai_models WHERE id = :modelId")
    suspend fun getModel(modelId: String): AiModelEntity?

    @Query("UPDATE ai_models SET isEnabledForChat = :enabled WHERE id = :modelId")
    suspend fun setEnabled(modelId: String, enabled: Boolean)

    @Upsert
    suspend fun upsertModels(models: List<AiModelEntity>)

    @Query("DELETE FROM ai_models WHERE id NOT IN (:modelIds)")
    suspend fun deleteModelsNotIn(modelIds: List<String>)
}
