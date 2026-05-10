package com.adam.xai_client.data.repository

import com.adam.xai_client.data.local.settings.SettingsDataStore
import com.adam.xai_client.domain.model.ApiSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SettingsRepository(
    private val settingsDataStore: SettingsDataStore
) {
    val apiSettings: Flow<ApiSettings> = settingsDataStore.apiSettings
    val lastSelectedModelId: Flow<String?> = settingsDataStore.lastSelectedModelId
    val lastSelectedRoleId: Flow<Long?> = settingsDataStore.lastSelectedRoleId
    val streamingHapticsEnabled: Flow<Boolean> = settingsDataStore.streamingHapticsEnabled
    val uiHapticsEnabled: Flow<Boolean> = settingsDataStore.uiHapticsEnabled

    suspend fun currentApiSettings(): ApiSettings = apiSettings.first()

    suspend fun saveApiSettings(apiKey: String, baseUrl: String) {
        settingsDataStore.saveApiSettings(apiKey = apiKey, baseUrl = baseUrl)
    }

    suspend fun setLastSelectedModelId(modelId: String?) {
        settingsDataStore.setLastSelectedModelId(modelId)
    }

    suspend fun setLastSelectedRoleId(roleId: Long?) {
        settingsDataStore.setLastSelectedRoleId(roleId)
    }

    suspend fun setStreamingHapticsEnabled(enabled: Boolean) {
        settingsDataStore.setStreamingHapticsEnabled(enabled)
    }

    suspend fun setUiHapticsEnabled(enabled: Boolean) {
        settingsDataStore.setUiHapticsEnabled(enabled)
    }
}
