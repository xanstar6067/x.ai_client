package com.adam.xai_client.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.adam.xai_client.domain.model.ReasoningEffort

@Entity(
    tableName = "chat_model_settings",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChatModelSettingsEntity(
    @PrimaryKey
    val chatId: Long,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val reasoningEffort: ReasoningEffort? = null,
    val contextMessageLimit: Int = 0,
    val webSearchEnabled: Boolean = false,
    val updatedAt: Long
)
