package com.adam.xai_client.domain.model

data class Chat(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedModelId: String?,
    val selectedRoleId: Long?,
    val cachedTokenCount: Int = 0,
    val lastPromptTokenCount: Int = 0,
    val lastCompletionTokenCount: Int = 0,
    val lastCachedTokenCount: Int = 0,
    val lastReasoningTokenCount: Int = 0
)
