package com.adam.xai_client.domain.model

data class Chat(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedModelId: String?,
    val selectedRoleId: Long?,
    val cachedTokenCount: Int = 0
)
