package com.adam.xai_client.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.repository.ModelRepository
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.ui.components.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelsUiState(
    val models: List<AiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class ModelsViewModel(
    private val modelRepository: ModelRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelRepository.models.collect { models ->
                _uiState.update { it.copy(models = models) }
            }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, message = null)
            }
            runCatching { modelRepository.refreshModels() }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "Список моделей обновлен.",
                            error = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.toUserMessage(),
                            message = null
                        )
                    }
                }
        }
    }

    fun setModelEnabled(modelId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { modelRepository.setModelEnabled(modelId, enabled) }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.toUserMessage()) }
                }
        }
    }

    fun clearTransientMessages() {
        _uiState.update { it.copy(error = null, message = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ModelsViewModel(modelRepository = container.modelRepository)
            }
        }
    }
}
