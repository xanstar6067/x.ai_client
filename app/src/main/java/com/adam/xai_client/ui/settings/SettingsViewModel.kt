package com.adam.xai_client.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.data.repository.SettingsRepository
import com.adam.xai_client.domain.model.ApiSettings
import com.adam.xai_client.ui.components.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = ApiSettings.DEFAULT_BASE_URL,
    val streamingHapticsEnabled: Boolean = true,
    val uiHapticsEnabled: Boolean = true,
    val isCheckingConnection: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val apiClient: XaiApiClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.apiSettings.collect { settings ->
                _uiState.update {
                    it.copy(
                        apiKey = settings.apiKey,
                        baseUrl = settings.baseUrl
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.streamingHapticsEnabled.collect { enabled ->
                _uiState.update { it.copy(streamingHapticsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.uiHapticsEnabled.collect { enabled ->
                _uiState.update { it.copy(uiHapticsEnabled = enabled) }
            }
        }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value, error = null, message = null) }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value, error = null, message = null) }
    }

    fun onStreamingHapticsChange(enabled: Boolean) {
        _uiState.update { it.copy(streamingHapticsEnabled = enabled, error = null, message = null) }
        viewModelScope.launch {
            settingsRepository.setStreamingHapticsEnabled(enabled)
        }
    }

    fun onUiHapticsChange(enabled: Boolean) {
        _uiState.update { it.copy(uiHapticsEnabled = enabled, error = null, message = null) }
        viewModelScope.launch {
            settingsRepository.setUiHapticsEnabled(enabled)
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.saveApiSettings(
                apiKey = state.apiKey,
                baseUrl = state.baseUrl
            )
            _uiState.update { it.copy(message = "Настройки сохранены.", error = null) }
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.apiKey.isBlank()) {
                _uiState.update { it.copy(error = "API-ключ не задан.", message = null) }
                return@launch
            }

            _uiState.update {
                it.copy(isCheckingConnection = true, error = null, message = null)
            }
            runCatching {
                apiClient.getModels(
                    apiKey = state.apiKey,
                    baseUrl = state.baseUrl.ifBlank { ApiSettings.DEFAULT_BASE_URL }
                )
            }.onSuccess { models ->
                _uiState.update {
                    it.copy(
                        isCheckingConnection = false,
                        message = "Подключение работает. Моделей: ${models.size}.",
                        error = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isCheckingConnection = false,
                        error = throwable.toUserMessage(),
                        message = null
                    )
                }
            }
        }
    }

    fun clearTransientMessages() {
        _uiState.update { it.copy(error = null, message = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    apiClient = container.apiClient
                )
            }
        }
    }
}
