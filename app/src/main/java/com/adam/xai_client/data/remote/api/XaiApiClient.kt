package com.adam.xai_client.data.remote.api

import com.adam.xai_client.data.remote.dto.ApiChatMessage
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.domain.model.ImageGenerationOptions
import com.adam.xai_client.domain.model.AiModel
import kotlinx.coroutines.flow.Flow

data class ChatStreamDelta(
    val content: String = "",
    val reasoningContent: String = "",
    val tokenUsage: TokenUsage? = null
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val cachedTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val imageTokens: Int = 0
)

interface XaiApiClient {
    suspend fun getModels(
        apiKey: String,
        baseUrl: String
    ): List<AiModel>

    suspend fun sendChatRequest(
        apiKey: String,
        baseUrl: String,
        modelId: String,
        messages: List<ApiChatMessage>,
        modelSettings: ChatModelSettings = ChatModelSettings()
    ): String

    fun streamChatRequest(
        apiKey: String,
        baseUrl: String,
        modelId: String,
        messages: List<ApiChatMessage>,
        modelSettings: ChatModelSettings = ChatModelSettings()
    ): Flow<ChatStreamDelta>

    suspend fun generateImage(
        apiKey: String,
        baseUrl: String,
        options: ImageGenerationOptions
    ): GeneratedImage
}
