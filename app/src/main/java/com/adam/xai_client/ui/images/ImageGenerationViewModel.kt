package com.adam.xai_client.ui.images

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.repository.ImageRepository
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.domain.model.ImageChat
import com.adam.xai_client.domain.model.ImageChatMessage
import com.adam.xai_client.domain.model.ImageGenerationOptions
import com.adam.xai_client.domain.model.MessageRole
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

data class ImageGenerationUiState(
    val prompt: String = "",
    val sourceImageUrl: String = "",
    val aspectRatio: String = "auto",
    val resolution: String = "1k",
    val chats: List<ImageChat> = emptyList(),
    val selectedChatId: Long? = null,
    val isNewChatMode: Boolean = false,
    val messages: List<ImageChatMessage> = emptyList(),
    val imageModels: List<AiModel> = emptyList(),
    val selectedModelId: String? = null,
    val isImageSettingsOpen: Boolean = false,
    val editingMessageId: Long? = null,
    val savedUri: Uri? = null,
    val isGenerating: Boolean = false,
    val isSavingMessageId: Long? = null,
    val error: String? = null,
    val message: String? = null
)

class ImageGenerationViewModel(
    private val imageRepository: ImageRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImageGenerationUiState())
    val uiState: StateFlow<ImageGenerationUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedChatMessages = uiState
        .map { it.selectedChatId }
        .distinctUntilChanged()
        .flatMapLatest { chatId -> imageRepository.observeMessages(chatId) }

    init {
        viewModelScope.launch {
            imageRepository.imageChats.collect { chats ->
                _uiState.update { state ->
                    val selectedChatId = state.selectedChatId
                        ?.takeIf { chatId -> chats.any { it.id == chatId } }
                    state.copy(chats = chats, selectedChatId = selectedChatId)
                }
            }
        }
        viewModelScope.launch {
            imageRepository.imageModels.collect { models ->
                _uiState.update { state ->
                    val selectedModelId = state.selectedModelId
                        ?.takeIf { modelId -> models.any { it.id == modelId } }
                        ?: state.chats.firstOrNull { it.id == state.selectedChatId }?.selectedModelId
                            ?.takeIf { modelId -> models.any { it.id == modelId } }
                        ?: models.firstOrNull()?.id
                    state.copy(imageModels = models, selectedModelId = selectedModelId)
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

    fun onAspectRatioChange(value: String) {
        _uiState.update { it.copy(aspectRatio = value, error = null, message = null) }
    }

    fun onResolutionChange(value: String) {
        _uiState.update { it.copy(resolution = value, error = null, message = null) }
    }

    fun onModelSelected(modelId: String) {
        _uiState.update { it.copy(selectedModelId = modelId, error = null, message = null) }
        viewModelScope.launch {
            _uiState.value.selectedChatId?.let { chatId ->
                imageRepository.updateChatSelection(
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
                ?.takeIf { modelId -> it.imageModels.any { model -> model.id == modelId } }
            it.copy(
                selectedChatId = chatId,
                isNewChatMode = chatId == null,
                selectedModelId = chatModelId ?: it.selectedModelId,
                editingMessageId = null,
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
                editingMessageId = null,
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
                editingMessageId = null,
                isImageSettingsOpen = false,
                error = null,
                message = null
            )
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            runCatching { imageRepository.deleteChat(chatId) }
                .onSuccess {
                    _uiState.update { state ->
                        if (state.selectedChatId == chatId) {
                            state.copy(
                                selectedChatId = null,
                                isNewChatMode = false,
                                messages = emptyList(),
                                editingMessageId = null,
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
            runCatching {
                imageRepository.deleteMessage(messageId)
                if (_uiState.value.editingMessageId == messageId) {
                    _uiState.update { it.copy(editingMessageId = null, sourceImageUrl = "") }
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.toUserMessage()) }
            }
        }
    }

    fun updateUserMessageText(messageId: Long, content: String) {
        viewModelScope.launch {
            if (_uiState.value.isGenerating) return@launch
            runCatching {
                imageRepository.updateUserMessageText(messageId, content)
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.toUserMessage()) }
            }
        }
    }

    fun editFromMessage(messageId: Long) {
        _uiState.update {
            it.copy(
                editingMessageId = messageId,
                sourceImageUrl = "",
                error = null,
                message = "Следующий запрос будет редактировать выбранную картинку."
            )
        }
    }

    fun clearEditSource() {
        _uiState.update { it.copy(editingMessageId = null, message = null, error = null) }
    }

    fun generate() {
        viewModelScope.launch {
            val state = _uiState.value
            val prompt = state.prompt.trim()
            if (prompt.isBlank()) {
                _uiState.update { it.copy(error = "Введите описание изображения.") }
                return@launch
            }
            val modelId = state.selectedModelId
            if (modelId.isNullOrBlank()) {
                _uiState.update {
                    it.copy(error = "Включите image/imagine модель на странице моделей и выберите ее здесь.")
                }
                return@launch
            }

            _uiState.update {
                it.copy(isGenerating = true, savedUri = null, error = null, message = null)
            }

            runCatching {
                val chatId = state.selectedChatId ?: imageRepository.createChat(
                    title = prompt.toImageChatTitle(),
                    selectedModelId = modelId
                )
                val parentMessageId = imageRepository.getVisibleTailMessageId(chatId)
                val sourceDataUrl = state.editingMessageId?.let { messageId ->
                    state.messages.firstOrNull { it.id == messageId }?.generatedImage
                        ?.let { imageRepository.imageAsDataUrl(it) }
                }
                val userMessageId = imageRepository.addUserMessage(
                    chatId = chatId,
                    content = prompt,
                    parentMessageId = parentMessageId
                )
                val image = imageRepository.generateImage(
                    ImageGenerationOptions(
                        modelId = modelId,
                        prompt = prompt,
                        aspectRatio = state.aspectRatio.takeUnless { it == "auto" },
                        resolution = state.resolution,
                        sourceImageUrl = sourceDataUrl ?: state.sourceImageUrl.trim().ifBlank { null }
                    )
                )
                imageRepository.addAssistantImageMessage(
                    chatId = chatId,
                    content = prompt,
                    image = image,
                    sourceMessageId = state.editingMessageId,
                    parentMessageId = userMessageId
                )
                imageRepository.updateChatAfterGeneration(
                    chatId = chatId,
                    title = prompt.toImageChatTitle(),
                    selectedModelId = modelId
                )
                chatId
            }.onSuccess { chatId ->
                _uiState.update {
                    it.copy(
                        selectedChatId = chatId,
                        isNewChatMode = false,
                        prompt = "",
                        sourceImageUrl = "",
                        editingMessageId = null,
                        isGenerating = false,
                        message = null,
                        error = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
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
            val chatId = state.selectedChatId ?: return@launch
            if (state.isGenerating) return@launch
            val assistant = state.messages.firstOrNull {
                it.id == messageId && it.role == MessageRole.ASSISTANT
            }
                ?: return@launch
            val parentMessageId = assistant.parentMessageId ?: return@launch
            val prompt = assistant.content.trim()
            if (prompt.isBlank()) return@launch
            val modelId = state.selectedModelId
            if (modelId.isNullOrBlank()) {
                _uiState.update { it.copy(error = "Select an image model first.") }
                return@launch
            }

            _uiState.update { it.copy(isGenerating = true, savedUri = null, error = null, message = null) }
            runCatching {
                val sourceDataUrl = assistant.sourceMessageId?.let { sourceId ->
                    state.messages.firstOrNull { it.id == sourceId }?.generatedImage
                        ?.let { imageRepository.imageAsDataUrl(it) }
                }
                val image = imageRepository.generateImage(
                    ImageGenerationOptions(
                        modelId = modelId,
                        prompt = prompt,
                        aspectRatio = state.aspectRatio.takeUnless { it == "auto" },
                        resolution = state.resolution,
                        sourceImageUrl = sourceDataUrl
                    )
                )
                imageRepository.addAssistantImageMessage(
                    chatId = chatId,
                    content = prompt,
                    image = image,
                    sourceMessageId = assistant.sourceMessageId,
                    parentMessageId = parentMessageId
                )
                imageRepository.updateChatAfterGeneration(
                    chatId = chatId,
                    title = prompt.toImageChatTitle(),
                    selectedModelId = modelId
                )
            }.onSuccess {
                _uiState.update { it.copy(isGenerating = false, error = null, message = null) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(isGenerating = false, error = throwable.toUserMessage()) }
            }
        }
    }

    fun switchMessageVersion(messageId: Long, direction: Int) {
        viewModelScope.launch {
            if (_uiState.value.isGenerating) return@launch
            runCatching {
                imageRepository.switchToSiblingVersion(messageId, direction)
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.toUserMessage()) }
            }
        }
    }

    fun save(messageId: Long) {
        viewModelScope.launch {
            val image = _uiState.value.messages
                .firstOrNull { it.id == messageId }
                ?.generatedImage
                ?: return@launch
            _uiState.update { it.copy(isSavingMessageId = messageId, error = null, message = null) }
            runCatching { imageRepository.saveImage(image) }
                .onSuccess { uri ->
                    _uiState.update {
                        it.copy(
                            isSavingMessageId = null,
                            savedUri = uri,
                            message = "Изображение сохранено в Pictures/xAI Chat.",
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

    fun setImageSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isImageSettingsOpen = isOpen) }
    }

    fun onStoragePermissionDenied() {
        _uiState.update { it.copy(error = "Нужно разрешение на запись, чтобы сохранить изображение в Pictures.") }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ImageGenerationViewModel(imageRepository = container.imageRepository)
            }
        }
    }
}

private fun String.toImageChatTitle(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .take(48)
        .ifBlank { "Новый image-чат" }
}
