package com.adam.xai_client.domain.model

data class AiModel(
    val id: String,
    val name: String = id,
    val isEnabledForChat: Boolean = false,
    val aliases: List<String> = emptyList(),
    val fingerprint: String? = null,
    val version: String? = null,
    val inputModalities: List<String> = emptyList(),
    val outputModalities: List<String> = emptyList(),
    val maxPromptLength: Int? = null,
    val promptTextTokenPrice: Int? = null,
    val cachedPromptTextTokenPrice: Int? = null,
    val completionTextTokenPrice: Int? = null,
    val promptImageTokenPrice: Int? = null,
    val searchPrice: Int? = null
)
