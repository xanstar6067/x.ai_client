package com.adam.xai_client.ui.videos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.repository.VideoRepository
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.ModelLimits
import com.adam.xai_client.domain.model.VideoChat
import com.adam.xai_client.domain.model.VideoChatMessage
import com.adam.xai_client.domain.model.VideoGenerationOptions
import com.adam.xai_client.domain.model.XaiModelLimits
import com.adam.xai_client.ui.components.toUserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoGenerationUiState(
    val prompt: String = "",
    val sourceImageUrl: String = "",
    val durationSeconds: Int = 5,
    val aspectRatio: String = "16:9",
    val resolution: String = "480p",
    val chats: List<VideoChat> = emptyList(),
    val selectedChatId: Long? = null,
    val isNewChatMode: Boolean = false,
    val messages: List<VideoChatMessage> = emptyList(),
    val videoModels: List<AiModel> = emptyList(),
    val selectedModelId: String? = null,
    val selectedModelLimits: ModelLimits? = null,
    val isModelInfoOpen: Boolean = false,
    val isVideoSettingsOpen: Boolean = false,
    val savedUri: Uri? = null,
    val isGenerating: Boolean = false,
    val generationProgress: Int? = null,
    val generationStatus: String? = null,
    val generationRequestId: String? = null,
    val isSavingMessageId: Long? = null,
    val error: String? = null,
    val message: String? = null
)

class VideoGenerationViewModel(
    private val videoRepository: VideoRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(VideoGenerationUiState())
    val uiState: StateFlow<VideoGenerationUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedChatMessages = uiState
        .map { it.selectedChatId }
        .distinctUntilChanged()
        .flatMapLatest { chatId -> videoRepository.observeMessages(chatId) }

    init {
        viewModelScope.launch {
            videoRepository.videoChats.collect { chats ->
                _uiState.update { state ->
                    val selectedChatId = state.selectedChatId
                        ?.takeIf { chatId -> chats.any { it.id == chatId } }
                    state.copy(chats = chats, selectedChatId = selectedChatId)
                }
            }
        }
        viewModelScope.launch {
            videoRepository.videoModels.collect { models ->
                _uiState.update { state ->
                    val selectedModelId = state.selectedModelId
                        ?.takeIf { modelId -> models.any { it.id == modelId } }
                        ?: state.chats.firstOrNull { it.id == state.selectedChatId }?.selectedModelId
                            ?.takeIf { modelId -> models.any { it.id == modelId } }
                        ?: models.firstOrNull()?.id
                    state.copy(
                        videoModels = models,
                        selectedModelId = selectedModelId,
                        selectedModelLimits = limitsForModelId(selectedModelId, models)
                    )
                }
            }
        }
        viewModelScope.launch {
            selectedChatMessages.collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun onPromptChange(value: String) {
        _uiState.update { it.copy(prompt = value, error = null, message = null) }
    }

    fun onSourceImageUrlChange(value: String) {
        _uiState.update { it.copy(sourceImageUrl = value, error = null, message = null) }
    }

    fun onDurationChange(value: Int) {
        _uiState.update { it.copy(durationSeconds = value.coerceIn(1, 15), error = null, message = null) }
    }

    fun onAspectRatioChange(value: String) {
        _uiState.update { it.copy(aspectRatio = value, error = null, message = null) }
    }

    fun onResolutionChange(value: String) {
        _uiState.update { it.copy(resolution = value, error = null, message = null) }
    }

    fun onModelSelected(modelId: String) {
        _uiState.update {
            it.copy(
                selectedModelId = modelId,
                selectedModelLimits = limitsForModelId(modelId, it.videoModels),
                error = null,
                message = null
            )
        }
        viewModelScope.launch {
            _uiState.value.selectedChatId?.let { chatId ->
                videoRepository.updateChatSelection(
                    chatId = chatId,
                    selectedModelId = modelId
                )
            }
        }
    }

    fun onChatSelected(chatId: Long?) {
        _uiState.update {
            val chatModelId = it.chats
                .firstOrNull { chat -> chat.id == chatId }
                ?.selectedModelId
                ?.takeIf { modelId -> it.videoModels.any { model -> model.id == modelId } }
            val selectedModelId = chatModelId ?: it.selectedModelId
            it.copy(
                selectedChatId = chatId,
                isNewChatMode = chatId == null,
                selectedModelId = selectedModelId,
                selectedModelLimits = limitsForModelId(selectedModelId, it.videoModels),
                sourceImageUrl = "",
                error = null,
                message = null
            )
        }
    }

    fun newChat() {
        _uiState.update {
            it.copy(
                selectedChatId = null,
                isNewChatMode = true,
                messages = emptyList(),
                prompt = "",
                sourceImageUrl = "",
                error = null,
                message = null
            )
        }
    }

    fun showChatList() {
        _uiState.update {
            it.copy(
                selectedChatId = null,
                isNewChatMode = false,
                messages = emptyList(),
                prompt = "",
                sourceImageUrl = "",
                isVideoSettingsOpen = false,
                isModelInfoOpen = false,
                error = null,
                message = null
            )
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            runCatching { videoRepository.deleteChat(chatId) }
                .onSuccess {
                    _uiState.update { state ->
                        if (state.selectedChatId == chatId) {
                            state.copy(
                                selectedChatId = null,
                                isNewChatMode = false,
                                messages = emptyList(),
                                sourceImageUrl = "",
                                error = null,
                                message = null
                            )
                        } else {
                            state
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.toUserMessage()) }
                }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            if (_uiState.value.isGenerating) return@launch
            runCatching { videoRepository.deleteMessage(messageId) }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.toUserMessage()) }
                }
        }
    }

    fun updateUserMessageText(messageId: Long, content: String) {
        viewModelScope.launch {
            if (_uiState.value.isGenerating) return@launch
            runCatching { videoRepository.updateUserMessageText(messageId, content) }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.toUserMessage()) }
                }
        }
    }

    fun generate() {
        viewModelScope.launch {
            val state = _uiState.value
            val prompt = state.prompt.trim()
            if (prompt.isBlank()) {
                _uiState.update { it.copy(error = "Введите описание видео.") }
                return@launch
            }
            val modelId = state.selectedModelId
            if (modelId.isNullOrBlank()) {
                _uiState.update {
                    it.copy(error = "Включите video/imagine модель на странице моделей и выберите ее здесь.")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isGenerating = true,
                    savedUri = null,
                    generationProgress = 0,
                    generationStatus = "pending",
                    generationRequestId = null,
                    error = null,
                    message = null
                )
            }

            runCatching {
                val chatId = state.selectedChatId ?: videoRepository.createChat(
                    title = prompt.toVideoChatTitle(),
                    selectedModelId = modelId
                )
                val parentMessageId = videoRepository.getVisibleTailMessageId(chatId)
                val sourceImageUrl = state.sourceImageUrl.trim().ifBlank { null }
                val userMessageId = videoRepository.addUserMessage(
                    chatId = chatId,
                    content = prompt,
                    sourceImageUrl = sourceImageUrl,
                    parentMessageId = parentMessageId
                )
                generateAssistantVideo(
                    chatId = chatId,
                    prompt = prompt,
                    modelId = modelId,
                    sourceImageUrl = sourceImageUrl,
                    parentMessageId = userMessageId,
                    state = state
                )
                chatId
            }.onSuccess { chatId ->
                _uiState.update {
                    it.copy(
                        selectedChatId = chatId,
                        isNewChatMode = false,
                        prompt = "",
                        sourceImageUrl = "",
                        isGenerating = false,
                        generationProgress = null,
                        generationStatus = null,
                        generationRequestId = null,
                        message = null,
                        error = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generationProgress = null,
                        generationStatus = null,
                        error = throwable.toUserMessage()
                    )
                }
            }
        }
    }

    fun generateFromUserMessage(messageId: Long) {
        viewModelScope.launch {
            val state = _uiState.value
            val chatId = state.selectedChatId ?: return@launch
            if (state.isGenerating) return@launch
            val userMessage = state.messages.firstOrNull {
                it.id == messageId && it.role == MessageRole.USER
            } ?: return@launch
            val modelId = state.selectedModelId
            if (modelId.isNullOrBlank()) {
                _uiState.update { it.copy(error = "Выберите video модель.") }
                return@launch
            }
            val prompt = userMessage.content.trim()
            if (prompt.isBlank()) return@launch

            _uiState.update {
                it.copy(
                    isGenerating = true,
                    savedUri = null,
                    generationProgress = 0,
                    generationStatus = "pending",
                    generationRequestId = null,
                    error = null,
                    message = null
                )
            }
            runCatching {
                generateAssistantVideo(
                    chatId = chatId,
                    prompt = prompt,
                    modelId = modelId,
                    sourceImageUrl = userMessage.sourceImageUrl,
                    parentMessageId = userMessage.id,
                    state = state
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generationProgress = null,
                        generationStatus = null,
                        generationRequestId = null,
                        error = null,
                        message = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generationProgress = null,
                        generationStatus = null,
                        error = throwable.toUserMessage()
                    )
                }
            }
        }
    }

    fun regenerateResponse(messageId: Long) {
        viewModelScope.launch {
            val state = _uiState.value
            val chatId = state.selectedChatId ?: return@launch
            if (state.isGenerating) return@launch
            val assistant = state.messages.firstOrNull {
                it.id == messageId && it.role == MessageRole.ASSISTANT
            } ?: return@launch
            val parentMessageId = assistant.parentMessageId ?: return@launch
            val modelId = state.selectedModelId
            if (modelId.isNullOrBlank()) {
                _uiState.update { it.copy(error = "Выберите video модель.") }
                return@launch
            }
            val prompt = assistant.content.trim()
            if (prompt.isBlank()) return@launch

            _uiState.update {
                it.copy(
                    isGenerating = true,
                    savedUri = null,
                    generationProgress = 0,
                    generationStatus = "pending",
                    generationRequestId = null,
                    error = null,
                    message = null
                )
            }
            runCatching {
                generateAssistantVideo(
                    chatId = chatId,
                    prompt = prompt,
                    modelId = modelId,
                    sourceImageUrl = assistant.sourceImageUrl,
                    parentMessageId = parentMessageId,
                    state = state
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generationProgress = null,
                        generationStatus = null,
                        generationRequestId = null,
                        error = null,
                        message = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generationProgress = null,
                        generationStatus = null,
                        error = throwable.toUserMessage()
                    )
                }
            }
        }
    }

    fun switchMessageVersion(messageId: Long, direction: Int) {
        viewModelScope.launch {
            if (_uiState.value.isGenerating) return@launch
            runCatching { videoRepository.switchToSiblingVersion(messageId, direction) }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.toUserMessage()) }
                }
        }
    }

    fun save(messageId: Long) {
        viewModelScope.launch {
            val video = _uiState.value.messages
                .firstOrNull { it.id == messageId }
                ?.generatedVideo
                ?: return@launch
            _uiState.update { it.copy(isSavingMessageId = messageId, error = null, message = null) }
            runCatching { videoRepository.saveVideo(video) }
                .onSuccess { uri ->
                    _uiState.update {
                        it.copy(
                            isSavingMessageId = null,
                            savedUri = uri,
                            message = "Видео сохранено в Movies/xAI Chat.",
                            error = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSavingMessageId = null, error = throwable.toUserMessage())
                    }
                }
        }
    }

    fun clearTransientMessages() {
        _uiState.update { it.copy(error = null, message = null) }
    }

    fun setVideoSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isVideoSettingsOpen = isOpen) }
    }

    fun setModelInfoOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isModelInfoOpen = isOpen) }
    }

    fun onStoragePermissionDenied() {
        _uiState.update { it.copy(error = "Нужно разрешение на запись, чтобы сохранить видео в Movies.") }
    }

    private suspend fun generateAssistantVideo(
        chatId: Long,
        prompt: String,
        modelId: String,
        sourceImageUrl: String?,
        parentMessageId: Long,
        state: VideoGenerationUiState
    ) {
        val (video, requestId) = videoRepository.generateVideo(
            options = VideoGenerationOptions(
                modelId = modelId,
                prompt = prompt,
                durationSeconds = state.durationSeconds,
                aspectRatio = state.aspectRatio,
                resolution = state.resolution,
                sourceImageUrl = sourceImageUrl
            ),
            onProgress = { progress ->
                _uiState.update {
                    it.copy(
                        generationProgress = progress.progress,
                        generationStatus = progress.status,
                        generationRequestId = progress.requestId
                    )
                }
            }
        )
        videoRepository.addAssistantVideoMessage(
            chatId = chatId,
            content = prompt,
            video = video,
            requestId = requestId,
            sourceImageUrl = sourceImageUrl,
            aspectRatio = state.aspectRatio,
            resolution = state.resolution,
            parentMessageId = parentMessageId
        )
        videoRepository.updateChatAfterGeneration(
            chatId = chatId,
            title = prompt.toVideoChatTitle(),
            selectedModelId = modelId
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VideoGenerationViewModel(videoRepository = container.videoRepository)
            }
        }
    }
}

private fun limitsForModelId(modelId: String?, models: List<AiModel>): ModelLimits? {
    val model = modelId?.let { selected ->
        models.firstOrNull { it.id == selected || selected in it.aliases }
    }
    return XaiModelLimits.forModel(model) ?: XaiModelLimits.forModel(modelId)
}

private fun String.toVideoChatTitle(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .take(48)
        .ifBlank { "Новый video-чат" }
}
