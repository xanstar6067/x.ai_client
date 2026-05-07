package com.adam.xai_client.data.local.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adam.xai_client.domain.model.ApiSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.xaiSettingsDataStore by preferencesDataStore(name = "xai_settings")

class SettingsDataStore(private val context: Context) {
    val apiSettings: Flow<ApiSettings> = context.xaiSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            ApiSettings(
                apiKey = preferences[API_KEY].orEmpty(),
                baseUrl = preferences[BASE_URL]?.takeIf { it.isNotBlank() }
                    ?: ApiSettings.DEFAULT_BASE_URL
            )
        }

    val lastSelectedModelId: Flow<String?> = context.xaiSettingsDataStore.data
        .map { preferences -> preferences[LAST_SELECTED_MODEL_ID] }

    val lastSelectedRoleId: Flow<Long?> = context.xaiSettingsDataStore.data
        .map { preferences -> preferences[LAST_SELECTED_ROLE_ID] }

    suspend fun saveApiSettings(apiKey: String, baseUrl: String) {
        context.xaiSettingsDataStore.edit { preferences ->
            preferences[API_KEY] = apiKey.trim()
            preferences[BASE_URL] = baseUrl.trim().ifBlank { ApiSettings.DEFAULT_BASE_URL }
        }
    }

    suspend fun setLastSelectedModelId(modelId: String?) {
        context.xaiSettingsDataStore.edit { preferences ->
            if (modelId == null) {
                preferences.remove(LAST_SELECTED_MODEL_ID)
            } else {
                preferences[LAST_SELECTED_MODEL_ID] = modelId
            }
        }
    }

    suspend fun setLastSelectedRoleId(roleId: Long?) {
        context.xaiSettingsDataStore.edit { preferences ->
            if (roleId == null) {
                preferences.remove(LAST_SELECTED_ROLE_ID)
            } else {
                preferences[LAST_SELECTED_ROLE_ID] = roleId
            }
        }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val BASE_URL = stringPreferencesKey("base_url")
        val LAST_SELECTED_MODEL_ID = stringPreferencesKey("last_selected_model_id")
        val LAST_SELECTED_ROLE_ID = longPreferencesKey("last_selected_role_id")
    }
}
