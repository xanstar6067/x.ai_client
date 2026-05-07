package com.adam.xai_client.data.remote.dto

import com.adam.xai_client.domain.model.ChatModelSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ApiChatMessage>,
    val stream: Boolean = false,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty")
    val presencePenalty: Double? = null,
    @SerialName("reasoning_effort")
    val reasoningEffort: String? = null
)

fun chatCompletionRequestDto(
    model: String,
    messages: List<ApiChatMessage>,
    stream: Boolean,
    settings: ChatModelSettings
): ChatCompletionRequestDto = ChatCompletionRequestDto(
    model = model,
    messages = messages,
    stream = stream,
    maxTokens = settings.maxTokens,
    temperature = settings.temperature,
    topP = settings.topP,
    frequencyPenalty = settings.frequencyPenalty,
    presencePenalty = settings.presencePenalty,
    reasoningEffort = settings.reasoningEffort?.apiName
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
