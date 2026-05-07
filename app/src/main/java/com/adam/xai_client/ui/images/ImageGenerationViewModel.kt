package com.adam.xai_client.ui.images

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.repository.ImageRepository
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.domain.model.ImageGenerationOptions
import com.adam.xai_client.ui.components.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageGenerationUiState(
    val prompt: String = "",
    val sourceImageUrl: String = "",
    val aspectRatio: String = "auto",
    val resolution: String = "1k",
    val generatedImage: GeneratedImage? = null,
    val savedUri: Uri? = null,
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class ImageGenerationViewModel(
    private val imageRepository: ImageRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImageGenerationUiState())
    val uiState: StateFlow<ImageGenerationUiState> = _uiState.asStateFlow()

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

    fun generate() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    generatedImage = null,
                    savedUri = null,
                    error = null,
                    message = null
                )
            }

            runCatching {
                imageRepository.generateImage(
                    ImageGenerationOptions(
                        prompt = state.prompt,
                        aspectRatio = state.aspectRatio.takeUnless { it == "auto" },
                        resolution = state.resolution,
                        sourceImageUrl = state.sourceImageUrl.trim().ifBlank { null }
                    )
                )
            }.onSuccess { image ->
                _uiState.update {
                    it.copy(
                        generatedImage = image,
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

    fun save() {
        viewModelScope.launch {
            val image = _uiState.value.generatedImage ?: return@launch
            _uiState.update { it.copy(isSaving = true, error = null, message = null) }
            runCatching { imageRepository.saveImage(image) }
                .onSuccess { uri ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedUri = uri,
                            message = "Изображение сохранено в Pictures/xAI Chat.",
                            error = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSaving = false, error = throwable.toUserMessage())
                    }
                }
        }
    }

    fun clearTransientMessages() {
        _uiState.update { it.copy(error = null, message = null) }
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
