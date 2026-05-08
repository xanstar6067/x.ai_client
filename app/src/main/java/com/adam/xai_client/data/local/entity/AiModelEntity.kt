package com.adam.xai_client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_models")
data class AiModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val isEnabledForChat: Boolean = false,
    val aliases: String? = null,
    val fingerprint: String? = null,
    val version: String? = null,
    val inputModalities: String? = null,
    val outputModalities: String? = null,
    val maxPromptLength: Int? = null,
    val promptTextTokenPrice: Int? = null,
    val cachedPromptTextTokenPrice: Int? = null,
    val completionTextTokenPrice: Int? = null,
    val promptImageTokenPrice: Int? = null,
    val searchPrice: Int? = null,
    val updatedAt: Long
)
