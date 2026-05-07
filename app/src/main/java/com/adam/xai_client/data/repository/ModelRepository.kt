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

    suspend fun ensureKnownModels() {
        upsertModelsPreservingEnabled(knownXaiModels())
    }

    suspend fun refreshModels() {
        val settings = settingsRepository.currentApiSettings()
        if (settings.apiKey.isBlank()) {
            throw IllegalStateException("API-ключ не задан.")
        }

        val remoteModels = apiClient.getModels(
            apiKey = settings.apiKey,
            baseUrl = settings.baseUrl
        )
        val models = (remoteModels + knownXaiModels()).distinctBy { it.id }
        upsertModelsPreservingEnabled(models)
    }

    private suspend fun upsertModelsPreservingEnabled(models: List<AiModel>) {
        val enabledById = aiModelDao.getModels().associate { it.id to it.isEnabledForChat }
        val now = System.currentTimeMillis()
        aiModelDao.upsertModels(
            models.map { model ->
                AiModelEntity(
                    id = model.id,
                    name = model.name.ifBlank { model.id },
                    isEnabledForChat = enabledById[model.id] ?: model.isEnabledForChat,
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

    private fun knownXaiModels(): List<AiModel> = listOf(
        AiModel(id = "grok-4.20-multi-agent", name = "grok-4.20-multi-agent"),
        AiModel(id = "grok-4.20-reasoning", name = "grok-4.20-reasoning"),
        AiModel(id = "grok-4.20-non-reasoning", name = "grok-4.20-non-reasoning"),
        AiModel(id = "grok-imagine-image", name = "grok-imagine-image"),
        AiModel(id = "grok-imagine-image-quality", name = "grok-imagine-image-quality"),
        AiModel(id = "grok-imagine-image-pro", name = "grok-imagine-image-pro")
    )
}
