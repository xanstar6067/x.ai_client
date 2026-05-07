package com.adam.xai_client.domain.model

data class AiModel(
    val id: String,
    val name: String = id,
    val isEnabledForChat: Boolean = false
)
