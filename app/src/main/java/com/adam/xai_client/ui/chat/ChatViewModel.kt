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
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.ModelLimits
import com.adam.xai_client.domain.model.ModelRole
import com.adam.xai_client.domain.model.ReasoningEffort
import com.adam.xai_client.domain.model.XaiModelLimits
import com.adam.xai_client.domain.token.TokenCounter
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
    val modelSettings: ChatModelSettings = ChatModelSettings(),
    val selectedModelLimits: ModelLimits? = null,
    val chatTokenCount: Int = 0,
    val inputTokenCount: Int = 0,
    val availableModels: List<AiModel> = emptyList(),
    val availableRoles: List<ModelRole> = emptyList(),
    val isSending: Boolean = false,
    val isModelInfoOpen: Boolean = false,
    val isModelSettingsOpen: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val initialChatId: Long?,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val roleRepository: RoleRepository,
    private val settingsRepository: SettingsRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val tokenCounter: TokenCounter
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
                    _uiState.update {
                        it.copy(
                            messages = messages,
                            chatTokenCount = messages.sumOf { message -> message.tokenCount }
                        )
                    }
                }
        }

        viewModelScope.launch {
            chatIdFlow.flatMapLatest { chatRepository.observeModelSettings(it) }
                .collect { settings ->
                    _uiState.update { state ->
                        val normalized = settings.normalizedForModel(state.selectedModelId)
                        state.copy(modelSettings = normalized)
                    }
                }
        }

        viewModelScope.launch {
            modelRepository.models.collect { models ->
                latestModels = models
                val selectedModelId = _uiState.value.selectedModelId
                    ?: models.firstOrNull { it.isEnabledForChat }?.id
                _uiState.update {
                    it.copy(
                        selectedModelId = selectedModelId,
                        selectedModelLimits = XaiModelLimits.forModel(selectedModelId),
                        modelSettings = it.modelSettings.normalizedForModel(selectedModelId)
                    )
                }
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
        _uiState.update {
            it.copy(
                inputText = value,
                inputTokenCount = tokenCounter.count(value),
                error = null
            )
        }
    }

    fun updateMessageText(messageId: Long, content: String) {
        viewModelScope.launch {
            if (content.isBlank()) {
                _uiState.update { it.copy(error = "Нельзя сохранить пустое сообщение.") }
                return@launch
            }
            runCatching {
                val message = _uiState.value.messages.firstOrNull { it.id == messageId }
                chatRepository.updateMessageText(
                    messageId = messageId,
                    content = content,
                    reasoningContent = message?.reasoningContent
                )
                _uiState.value.chatId?.let { chatRepository.touchChat(it) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.toUserMessage()) }
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            if (_uiState.value.isSending) return@launch
            runCatching {
                chatRepository.deleteMessage(messageId)
                _uiState.value.chatId?.let { chatRepository.touchChat(it) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.toUserMessage()) }
            }
        }
    }

    fun onModelSelected(modelId: String) {
        _uiState.update {
            it.copy(
                selectedModelId = modelId,
                selectedModelLimits = XaiModelLimits.forModel(modelId),
                modelSettings = it.modelSettings.normalizedForModel(modelId),
                error = null
            )
        }
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
        persistModelSettings()
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

    fun sendMessage() {
        viewModelScope.launch {
            val outgoingText = _uiState.value.inputText
            _uiState.update { it.copy(isSending = true, error = null, inputText = "", inputTokenCount = 0) }

            runCatching {
                val state = _uiState.value
                sendMessageUseCase(
                    chatId = state.chatId,
                    input = outgoingText,
                    selectedModelId = state.selectedModelId,
                    selectedRoleId = state.selectedRoleId,
                    modelSettings = state.modelSettings,
                    onChatReady = { newChatId ->
                        chatIdFlow.value = newChatId
                        _uiState.update { it.copy(chatId = newChatId) }
                    }
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
            }.onFailure { throwable ->
                val failedChatId = (throwable as? MessageSendFailedException)?.chatId
                if (failedChatId != null) {
                    chatIdFlow.value = failedChatId
                }
                _uiState.update {
                    it.copy(
                        chatId = failedChatId ?: it.chatId,
                        inputText = if (failedChatId == null) outgoingText else "",
                        inputTokenCount = if (failedChatId == null) tokenCounter.count(outgoingText) else 0,
                        isSending = false,
                        error = throwable.toUserMessage()
                    )
                }
            }
        }
    }

    fun regenerateLastResponse() {
        val messageId = _uiState.value.messages
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.id
            ?: return
        regenerateResponse(messageId)
    }

    fun regenerateResponse(messageId: Long) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isSending || state.chatId == null) return@launch

            val assistantIndex = state.messages.indexOfFirst {
                it.id == messageId && it.role == MessageRole.ASSISTANT
            }
            if (assistantIndex <= 0) return@launch

            val userMessage = state.messages
                .take(assistantIndex)
                .lastOrNull { it.role == MessageRole.USER }
                ?: return@launch

            _uiState.update { it.copy(isSending = true, error = null) }
            runCatching {
                sendMessageUseCase(
                    chatId = state.chatId,
                    input = userMessage.content,
                    selectedModelId = state.selectedModelId,
                    selectedRoleId = state.selectedRoleId,
                    modelSettings = state.modelSettings,
                    addUserMessage = false,
                    parentMessageId = userMessage.id
                )
            }.onSuccess { newChatId ->
                chatIdFlow.value = newChatId
                _uiState.update {
                    it.copy(chatId = newChatId, isSending = false, error = null)
                }
            }.onFailure { throwable ->
                val failedChatId = (throwable as? MessageSendFailedException)?.chatId
                if (failedChatId != null) {
                    chatIdFlow.value = failedChatId
                }
                _uiState.update {
                    it.copy(
                        chatId = failedChatId ?: it.chatId,
                        isSending = false,
                        error = throwable.toUserMessage()
                    )
                }
            }
        }
    }

    fun resendFromUserMessage(messageId: Long) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isSending || state.chatId == null) return@launch

            val userIndex = state.messages.indexOfFirst {
                it.id == messageId && it.role == MessageRole.USER
            }
            if (userIndex < 0) return@launch

            val userMessage = state.messages[userIndex]

            _uiState.update { it.copy(isSending = true, error = null) }
            runCatching {
                sendMessageUseCase(
                    chatId = state.chatId,
                    input = userMessage.content,
                    selectedModelId = state.selectedModelId,
                    selectedRoleId = state.selectedRoleId,
                    modelSettings = state.modelSettings,
                    addUserMessage = false,
                    parentMessageId = userMessage.id
                )
            }.onSuccess { newChatId ->
                chatIdFlow.value = newChatId
                _uiState.update {
                    it.copy(chatId = newChatId, isSending = false, error = null)
                }
            }.onFailure { throwable ->
                val failedChatId = (throwable as? MessageSendFailedException)?.chatId
                if (failedChatId != null) {
                    chatIdFlow.value = failedChatId
                }
                _uiState.update {
                    it.copy(
                        chatId = failedChatId ?: it.chatId,
                        isSending = false,
                        error = throwable.toUserMessage()
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setModelInfoOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isModelInfoOpen = isOpen) }
    }

    fun setModelSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isModelSettingsOpen = isOpen) }
    }

    fun updateMaxTokens(maxTokens: Int?) {
        updateModelSettings { current ->
            val limit = XaiModelLimits.forModel(_uiState.value.selectedModelId)?.contextWindowTokens
            current.copy(maxTokens = maxTokens?.coerceIn(1, limit ?: 131_072))
        }
    }

    fun updateTemperature(temperature: Double?) {
        updateModelSettings { current -> current.copy(temperature = temperature?.coerceIn(0.0, 2.0)) }
    }

    fun updateTopP(topP: Double?) {
        updateModelSettings { current -> current.copy(topP = topP?.coerceIn(0.0, 1.0)) }
    }

    fun updateFrequencyPenalty(frequencyPenalty: Double?) {
        updateModelSettings { current ->
            current.copy(frequencyPenalty = frequencyPenalty?.coerceIn(-2.0, 2.0))
        }
    }

    fun updatePresencePenalty(presencePenalty: Double?) {
        updateModelSettings { current ->
            current.copy(presencePenalty = presencePenalty?.coerceIn(-2.0, 2.0))
        }
    }

    fun updateReasoningEffort(reasoningEffort: ReasoningEffort?) {
        updateModelSettings { current ->
            current.copy(
                reasoningEffort = reasoningEffort.takeIf {
                    _uiState.value.selectedModelId.supportsReasoningEffort()
                }
            )
        }
    }

    fun switchMessageVersion(messageId: Long, direction: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isSending) return@launch
            runCatching {
                chatRepository.switchToSiblingVersion(messageId, direction)
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.toUserMessage()) }
            }
        }
    }

    fun updateContextMessageLimit(contextMessageLimit: Int) {
        updateModelSettings { current ->
            current.copy(contextMessageLimit = contextMessageLimit.coerceIn(0, 99))
        }
    }

    fun updateWebSearchEnabled(enabled: Boolean) {
        updateModelSettings { current ->
            current.copy(webSearchEnabled = enabled)
        }
    }

    fun resetModelSettings() {
        updateModelSettings { ChatModelSettings(chatId = it.chatId) }
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
        _uiState.update {
            it.copy(
                availableModels = available,
                selectedModelLimits = XaiModelLimits.forModel(selectedModelId)
            )
        }
    }

    private fun updateModelSettings(transform: (ChatModelSettings) -> ChatModelSettings) {
        _uiState.update { state ->
            state.copy(
                modelSettings = transform(state.modelSettings)
                    .copy(chatId = state.chatId)
                    .normalizedForModel(state.selectedModelId),
                error = null
            )
        }
        persistModelSettings()
    }

    private fun persistModelSettings() {
        val state = _uiState.value
        val chatId = state.chatId ?: return
        viewModelScope.launch {
            chatRepository.updateModelSettings(chatId, state.modelSettings.copy(chatId = chatId))
        }
    }

    private fun ChatModelSettings.normalizedForModel(modelId: String?): ChatModelSettings {
        val limit = XaiModelLimits.forModel(modelId)?.contextWindowTokens
        val isMultiAgent = modelId.isGrok420MultiAgent()
        return copy(
            maxTokens = maxTokens
                ?.takeUnless { isMultiAgent }
                ?.coerceIn(1, limit ?: 131_072),
            frequencyPenalty = frequencyPenalty.takeUnless { isMultiAgent },
            presencePenalty = presencePenalty.takeUnless { isMultiAgent },
            reasoningEffort = reasoningEffort.takeIf { modelId.supportsReasoningEffort() },
            contextMessageLimit = contextMessageLimit.coerceIn(0, 99),
            webSearchEnabled = webSearchEnabled
        )
    }

    private fun String?.supportsReasoningEffort(): Boolean {
        val normalized = orEmpty().lowercase()
        return normalized.startsWith("grok-3-mini") || normalized.startsWith("grok-4.20-multi-agent")
    }

    private fun String?.isGrok420MultiAgent(): Boolean {
        return orEmpty().lowercase().startsWith("grok-4.20-multi-agent")
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
                    sendMessageUseCase = container.sendMessageUseCase,
                    tokenCounter = container.tokenCounter
                )
            }
        }
    }
}
