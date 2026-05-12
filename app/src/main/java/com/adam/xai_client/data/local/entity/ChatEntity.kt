package com.adam.xai_client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedModelId: String?,
    val selectedRoleId: Long?,
    val cachedTokenCount: Int = 0,
    val totalPromptTokenCount: Int = 0,
    val totalCompletionTokenCount: Int = 0,
    val totalImageTokenCount: Int = 0,
    val totalReasoningTokenCount: Int = 0,
    val lastPromptTokenCount: Int = 0,
    val lastCompletionTokenCount: Int = 0,
    val lastCachedTokenCount: Int = 0,
    val lastReasoningTokenCount: Int = 0,
    val accumulatedCostMicros: Long = 0,
    val lastRequestCostMicros: Long = 0
)
