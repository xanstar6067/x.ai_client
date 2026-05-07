package com.adam.xai_client.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.repository.ChatRepository
import com.adam.xai_client.data.repository.ModelRepository
import com.adam.xai_client.data.repository.RoleRepository
import com.adam.xai_client.data.repository.SettingsRepository
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.ModelRole
import com.adam.xai_client.domain.usecase.MessageSendFailedException
import com.adam.xai_client.domain.usecase.SendMessageUseCase
import com.adam.xai_client.ui.components.toUserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val chatId: Long? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val selectedModelId: String? = null,
    val selectedRoleId: Long? = null,
    val availableModels: List<AiModel> = emptyList(),
    val availableRoles: List<ModelRole> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val initialChatId: Long?,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val roleRepository: RoleRepository,
    private val settingsRepository: SettingsRepository,
    private val sendMessageUseCase: SendMessageUseCase
) : ViewModel() {
    private val chatIdFlow = MutableStateFlow(initialChatId)
    private val _uiState = MutableStateFlow(ChatUiState(chatId = initialChatId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var latestModels: List<AiModel> = emptyList()

    init {
        viewModelScope.launch {
            roleRepository.ensureBuiltInRole()
            if (initialChatId == null) {
                val lastModel = settingsRepository.lastSelectedModelId.first()
                val lastRole = settingsRepository.lastSelectedRoleId.first()
                    ?: roleRepository.getDefaultRole()?.id
                _uiState.update {
                    it.copy(
                        selectedModelId = lastModel,
                        selectedRoleId = lastRole
                    )
                }
                updateAvailableModels(lastModel)
            }
        }

        viewModelScope.launch {
            chatIdFlow.flatMapLatest { chatRepository.observeChat(it) }
                .collect { chat ->
                    if (chat != null) {
                        _uiState.update {
                            it.copy(
                                chatId = chat.id,
                                selectedModelId = chat.selectedModelId ?: it.selectedModelId,
                                selectedRoleId = chat.selectedRoleId ?: it.selectedRoleId
                            )
                        }
                        updateAvailableModels(chat.selectedModelId)
                    }
                }
        }

        viewModelScope.launch {
            chatIdFlow.flatMapLatest { chatRepository.observeMessages(it) }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
        }

        viewModelScope.launch {
            modelRepository.models.collect { models ->
                latestModels = models
                val selectedModelId = _uiState.value.selectedModelId
                    ?: models.firstOrNull { it.isEnabledForChat }?.id
                _uiState.update { it.copy(selectedModelId = selectedModelId) }
                updateAvailableModels(selectedModelId)
            }
        }

        viewModelScope.launch {
            roleRepository.roles.collect { roles ->
                val selectedRoleId = _uiState.value.selectedRoleId
                    ?.takeIf { id -> roles.any { it.id == id } }
                    ?: roles.firstOrNull { it.isDefault }?.id
                    ?: roles.firstOrNull()?.id
                _uiState.update {
                    it.copy(
                        availableRoles = roles,
                        selectedRoleId = selectedRoleId
                    )
                }
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(inputText = value, error = null) }
    }

    fun onModelSelected(modelId: String) {
        _uiState.update { it.copy(selectedModelId = modelId, error = null) }
        updateAvailableModels(modelId)
        viewModelScope.launch {
            settingsRepository.setLastSelectedModelId(modelId)
            _uiState.value.chatId?.let { chatId ->
                chatRepository.updateChatSelection(
                    chatId = chatId,
                    selectedModelId = modelId,
                    selectedRoleId = _uiState.value.selectedRoleId
                )
            }
        }
    }

    fun onRoleSelected(roleId: Long) {
        _uiState.update { it.copy(selectedRoleId = roleId, error = null) }
        viewModelScope.launch {
            settingsRepository.setLastSelectedRoleId(roleId)
            _uiState.value.chatId?.let { chatId ->
                chatRepository.updateChatSelection(
                    chatId = chatId,
                    selectedModelId = _uiState.value.selectedModelId,
                    selectedRoleId = roleId
                )
            }
        }
    }

    fun sendMessage(onChatCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val beforeSendChatId = _uiState.value.chatId
            _uiState.update { it.copy(isSending = true, error = null) }

            runCatching {
                val state = _uiState.value
                sendMessageUseCase(
                    chatId = state.chatId,
                    input = state.inputText,
                    selectedModelId = state.selectedModelId,
                    selectedRoleId = state.selectedRoleId
                )
            }.onSuccess { newChatId ->
                chatIdFlow.value = newChatId
                _uiState.update {
                    it.copy(
                        chatId = newChatId,
                        inputText = "",
                        isSending = false,
                        error = null
                    )
                }
                if (beforeSendChatId == null) {
                    onChatCreated(newChatId)
                }
            }.onFailure { throwable ->
                val failedChatId = (throwable as? MessageSendFailedException)?.chatId
                if (failedChatId != null) {
                    chatIdFlow.value = failedChatId
                }
                _uiState.update {
                    it.copy(
                        chatId = failedChatId ?: it.chatId,
                        inputText = if (failedChatId == null) it.inputText else "",
                        isSending = false,
                        error = throwable.toUserMessage()
                    )
                }
                if (beforeSendChatId == null && failedChatId != null) {
                    onChatCreated(failedChatId)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun updateAvailableModels(selectedModelId: String?) {
        val enabledModels = latestModels.filter { it.isEnabledForChat }
        val selectedModel = selectedModelId?.let { selected ->
            latestModels.firstOrNull { it.id == selected } ?: AiModel(
                id = selected,
                name = selected,
                isEnabledForChat = true
            )
        }
        val available = (enabledModels + listOfNotNull(selectedModel))
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
        _uiState.update { it.copy(availableModels = available) }
    }

    companion object {
        fun factory(
            container: AppContainer,
            chatId: Long?
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    initialChatId = chatId,
                    chatRepository = container.chatRepository,
                    modelRepository = container.modelRepository,
                    roleRepository = container.roleRepository,
                    settingsRepository = container.settingsRepository,
                    sendMessageUseCase = container.sendMessageUseCase
                )
            }
        }
    }
}
