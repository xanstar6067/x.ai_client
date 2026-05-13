package com.adam.xai_client.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.data.remote.dto.ApiChatMessage
import com.adam.xai_client.data.repository.ModelRepository
import com.adam.xai_client.data.repository.SettingsRepository
import com.adam.xai_client.domain.model.ApiSettings
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.isTextChatModel
import com.adam.xai_client.ui.components.toUserMessage
import com.adam.xai_client.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = ApiSettings.DEFAULT_BASE_URL,
    val availableNamingModels: List<AiModel> = emptyList(),
    val selectedNamingModelId: String? = null,
    val promptCachingEnabled: Boolean = true,
    val streamingHapticsEnabled: Boolean = true,
    val uiHapticsEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.Default,
    val isCheckingConnection: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository,
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
                        baseUrl = settings.baseUrl,
                        promptCachingEnabled = settings.promptCachingEnabled
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
        viewModelScope.launch {
            settingsRepository.themeMode.collect { themeMode ->
                _uiState.update { it.copy(themeMode = themeMode) }
            }
        }
        viewModelScope.launch {
            modelRepository.enabledModels.collect { models ->
                val namingModels = models
                    .filter { it.isTextChatModel() }
                    .sortedBy { it.name.lowercase() }
                val selected = _uiState.value.selectedNamingModelId
                    ?.takeIf { selectedId -> namingModels.any { it.id == selectedId } }
                    ?: settingsRepository.chatNamingModelId.first()
                        ?.takeIf { selectedId -> namingModels.any { it.id == selectedId } }
                    ?: namingModels.firstOrNull()?.id
                _uiState.update {
                    it.copy(
                        availableNamingModels = namingModels,
                        selectedNamingModelId = selected
                    )
                }
                if (selected != null && selected != settingsRepository.chatNamingModelId.first()) {
                    settingsRepository.setChatNamingModelId(selected)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.chatNamingModelId.collect { modelId ->
                val selected = modelId?.takeIf { id ->
                    _uiState.value.availableNamingModels.any { it.id == id }
                } ?: _uiState.value.availableNamingModels.firstOrNull()?.id
                _uiState.update { it.copy(selectedNamingModelId = selected) }
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

    fun onThemeModeChange(themeMode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = themeMode, error = null, message = null) }
        viewModelScope.launch {
            settingsRepository.setThemeMode(themeMode)
        }
    }

    fun onPromptCachingChange(enabled: Boolean) {
        _uiState.update { it.copy(promptCachingEnabled = enabled, error = null, message = null) }
        viewModelScope.launch {
            settingsRepository.setPromptCachingEnabled(enabled)
        }
    }

    fun onNamingModelSelected(modelId: String) {
        _uiState.update { it.copy(selectedNamingModelId = modelId, error = null, message = null) }
        viewModelScope.launch {
            settingsRepository.setChatNamingModelId(modelId)
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
                val baseUrl = state.baseUrl.ifBlank { ApiSettings.DEFAULT_BASE_URL }
                val namingModelId = state.selectedNamingModelId?.trim().orEmpty()
                if (namingModelId.isBlank()) {
                    val models = apiClient.getModels(
                        apiKey = state.apiKey,
                        baseUrl = baseUrl
                    )
                    "Подключение работает. Моделей: ${models.size}."
                } else {
                    apiClient.sendChatRequest(
                        apiKey = state.apiKey,
                        baseUrl = baseUrl,
                        modelId = namingModelId,
                        messages = listOf(
                            ApiChatMessage(
                                role = MessageRole.SYSTEM.apiName,
                                content = "Ответь одним словом: OK."
                            ),
                            ApiChatMessage(
                                role = MessageRole.USER.apiName,
                                content = "Проверка доступности модели."
                            )
                        ),
                        modelSettings = ChatModelSettings(maxTokens = 4, temperature = 0.0)
                    )
                    "Модель \"$namingModelId\" доступна."
                }
            }.onSuccess { successMessage ->
                _uiState.update {
                    it.copy(
                        isCheckingConnection = false,
                        message = successMessage,
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
                    modelRepository = container.modelRepository,
                    apiClient = container.apiClient
                )
            }
        }
    }
}
