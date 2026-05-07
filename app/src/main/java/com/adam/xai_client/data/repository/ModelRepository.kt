package com.adam.xai_client.data.repository

import com.adam.xai_client.data.local.dao.AiModelDao
import com.adam.xai_client.data.local.entity.AiModelEntity
import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.domain.model.AiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelRepository(
    private val aiModelDao: AiModelDao,
    private val settingsRepository: SettingsRepository,
    private val apiClient: XaiApiClient
) {
    val models: Flow<List<AiModel>> = aiModelDao.observeModels()
        .map { entities -> entities.map { it.asDomain() } }

    val enabledModels: Flow<List<AiModel>> = aiModelDao.observeEnabledModels()
        .map { entities -> entities.map { it.asDomain() } }

    suspend fun refreshModels() {
        val settings = settingsRepository.currentApiSettings()
        if (settings.apiKey.isBlank()) {
            throw IllegalStateException("API-ключ не задан.")
        }

        val remoteModels = apiClient.getModels(
            apiKey = settings.apiKey,
            baseUrl = settings.baseUrl
        )
        val enabledById = aiModelDao.getModels().associate { it.id to it.isEnabledForChat }
        val now = System.currentTimeMillis()
        aiModelDao.upsertModels(
            remoteModels.map { model ->
                AiModelEntity(
                    id = model.id,
                    name = model.name.ifBlank { model.id },
                    isEnabledForChat = enabledById[model.id] ?: false,
                    updatedAt = now
                )
            }
        )
    }

    suspend fun setModelEnabled(modelId: String, enabled: Boolean) {
        aiModelDao.setEnabled(modelId = modelId, enabled = enabled)
    }

    suspend fun getModel(modelId: String): AiModel? {
        return aiModelDao.getModel(modelId)?.asDomain()
    }
}
