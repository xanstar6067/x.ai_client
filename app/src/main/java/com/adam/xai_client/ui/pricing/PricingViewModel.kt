package com.adam.xai_client.ui.pricing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.repository.ModelRepository
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.toTokenPriceTicksOrNull
import com.adam.xai_client.domain.model.toUsdPriceInput
import com.adam.xai_client.domain.model.withKnownTokenPricingFallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PricingUiState(
    val models: List<AiModel> = emptyList(),
    val drafts: Map<String, ModelPriceDraft> = emptyMap(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class ModelPriceDraft(
    val input: String = "",
    val cachedInput: String = "",
    val output: String = ""
)

enum class PriceField {
    INPUT,
    CACHED_INPUT,
    OUTPUT
}

class PricingViewModel(
    private val modelRepository: ModelRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PricingUiState())
    val uiState: StateFlow<PricingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelRepository.models.collect { models ->
                _uiState.update { state ->
                    val mergedDrafts = models.associate { model ->
                        model.id to (state.drafts[model.id] ?: model.toDraft())
                    }
                    state.copy(
                        models = models,
                        drafts = mergedDrafts
                    )
                }
            }
        }
    }

    fun updatePrice(modelId: String, field: PriceField, value: String) {
        _uiState.update { state ->
            val current = state.drafts[modelId] ?: ModelPriceDraft()
            val sanitized = value.filter { it.isDigit() || it == '.' || it == ',' }.take(12)
            val updated = when (field) {
                PriceField.INPUT -> current.copy(input = sanitized)
                PriceField.CACHED_INPUT -> current.copy(cachedInput = sanitized)
                PriceField.OUTPUT -> current.copy(output = sanitized)
            }
            state.copy(
                drafts = state.drafts + (modelId to updated),
                error = null,
                message = null
            )
        }
    }

    fun autofillKnownPrices() {
        _uiState.update { state ->
            state.copy(
                drafts = state.models.associate { model ->
                    model.id to model.withKnownTokenPricingFallback().toDraft()
                },
                message = "Цены подставлены из известных данных моделей.",
                error = null
            )
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val parsed = state.models.map { model ->
                val draft = state.drafts[model.id] ?: ModelPriceDraft()
                model.id to (draft.toParsedPrices() ?: run {
                    _uiState.update {
                        it.copy(error = "Проверьте цены для ${model.name.ifBlank { model.id }}.")
                    }
                    return@launch
                })
            }
            _uiState.update { it.copy(isSaving = true, error = null, message = null) }
            runCatching {
                parsed.forEach { (modelId, prices) ->
                    modelRepository.updateTokenPrices(
                        modelId = modelId,
                        promptTextTokenPrice = prices.input,
                        cachedPromptTextTokenPrice = prices.cachedInput,
                        completionTextTokenPrice = prices.output
                    )
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(isSaving = false, message = "Расценки сохранены.", error = null)
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = throwable.message ?: "Не удалось сохранить расценки."
                    )
                }
            }
        }
    }

    fun clearTransientMessages() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    private fun AiModel.toDraft(): ModelPriceDraft = ModelPriceDraft(
        input = promptTextTokenPrice?.toUsdPriceInput().orEmpty(),
        cachedInput = cachedPromptTextTokenPrice?.toUsdPriceInput().orEmpty(),
        output = completionTextTokenPrice?.toUsdPriceInput().orEmpty()
    )

    private data class ParsedPrices(
        val input: Int?,
        val cachedInput: Int?,
        val output: Int?
    )

    private fun ModelPriceDraft.toParsedPrices(): ParsedPrices? {
        val inputPrice = input.toTokenPriceTicksOrNull()
        val cachedInputPrice = cachedInput.toTokenPriceTicksOrNull()
        val outputPrice = output.toTokenPriceTicksOrNull()
        if (input.isNotBlank() && inputPrice == null) return null
        if (cachedInput.isNotBlank() && cachedInputPrice == null) return null
        if (output.isNotBlank() && outputPrice == null) return null
        return ParsedPrices(
            input = inputPrice,
            cachedInput = cachedInputPrice,
            output = outputPrice
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PricingViewModel(modelRepository = container.modelRepository)
            }
        }
    }
}
