package com.adam.xai_client.data.remote.dto

import com.adam.xai_client.domain.model.AiModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelsResponseDto(
    val data: List<ModelDto> = emptyList()
)

@Serializable
data class LanguageModelsResponseDto(
    val models: List<ModelDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String,
    val aliases: List<String> = emptyList(),
    val fingerprint: String? = null,
    val version: String? = null,
    @SerialName("input_modalities")
    val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities")
    val outputModalities: List<String> = emptyList(),
    @SerialName("max_prompt_length")
    val maxPromptLength: Int? = null,
    @SerialName("prompt_text_token_price")
    val promptTextTokenPrice: Int? = null,
    @SerialName("cached_prompt_text_token_price")
    val cachedPromptTextTokenPrice: Int? = null,
    @SerialName("completion_text_token_price")
    val completionTextTokenPrice: Int? = null,
    @SerialName("prompt_image_token_price")
    val promptImageTokenPrice: Int? = null,
    @SerialName("search_price")
    val searchPrice: Int? = null
)

fun ModelDto.asDomain(): AiModel = AiModel(
    id = id,
    name = aliases.firstOrNull() ?: id,
    aliases = aliases,
    fingerprint = fingerprint,
    version = version,
    inputModalities = inputModalities,
    outputModalities = outputModalities,
    maxPromptLength = maxPromptLength,
    promptTextTokenPrice = promptTextTokenPrice,
    cachedPromptTextTokenPrice = cachedPromptTextTokenPrice,
    completionTextTokenPrice = completionTextTokenPrice,
    promptImageTokenPrice = promptImageTokenPrice,
    searchPrice = searchPrice
)
