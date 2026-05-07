package com.adam.xai_client.data.remote.api

import com.adam.xai_client.data.remote.dto.ApiChatMessage
import com.adam.xai_client.domain.model.AiModel
import kotlinx.coroutines.flow.Flow

data class ChatStreamDelta(
    val content: String = "",
    val reasoningContent: String = ""
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
        messages: List<ApiChatMessage>
    ): String

    fun streamChatRequest(
        apiKey: String,
        baseUrl: String,
        modelId: String,
        messages: List<ApiChatMessage>
    ): Flow<ChatStreamDelta>
}
