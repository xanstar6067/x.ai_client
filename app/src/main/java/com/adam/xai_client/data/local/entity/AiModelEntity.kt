package com.adam.xai_client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_models")
data class AiModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val isEnabledForChat: Boolean = false,
    val updatedAt: Long
)
