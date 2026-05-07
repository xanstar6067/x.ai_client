package com.adam.xai_client.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ApiChatMessage>,
    val stream: Boolean = false
)

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChatChoiceDto> = emptyList()
)

@Serializable
data class ChatChoiceDto(
    val message: ChatMessageDto? = null,
    val delta: ChatMessageDto? = null
)

@Serializable
data class ChatMessageDto(
    val role: String? = null,
    val content: String? = null,
    val reasoning_content: String? = null
)
