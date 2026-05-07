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

@Serializable
data class ResponsesRequestDto(
    val model: String,
    val input: List<ApiChatMessage>,
    val stream: Boolean = false,
    @SerialName("max_output_tokens")
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    val reasoning: ResponsesReasoningDto? = null
)

@Serializable
data class ResponsesReasoningDto(
    val effort: String
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

fun responsesRequestDto(
    model: String,
    messages: List<ApiChatMessage>,
    stream: Boolean,
    settings: ChatModelSettings
): ResponsesRequestDto = ResponsesRequestDto(
    model = model,
    input = messages,
    stream = stream,
    maxOutputTokens = settings.maxTokens,
    temperature = settings.temperature,
    topP = settings.topP,
    reasoning = settings.reasoningEffort?.let { ResponsesReasoningDto(it.apiName) }
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

@Serializable
data class ResponsesResponseDto(
    val output: List<ResponsesOutputDto> = emptyList(),
    @SerialName("output_text")
    val outputText: String? = null
)

@Serializable
data class ResponsesOutputDto(
    val type: String? = null,
    val content: List<ResponsesContentDto> = emptyList()
)

@Serializable
data class ResponsesContentDto(
    val type: String? = null,
    val text: String? = null
)

fun ResponsesResponseDto.outputTextContent(): String {
    if (!outputText.isNullOrBlank()) return outputText
    return output
        .flatMap { it.content }
        .mapNotNull { it.text }
        .joinToString(separator = "")
}

@Serializable
data class ResponsesStreamEventDto(
    val type: String? = null,
    val delta: String? = null,
    val text: String? = null,
    val response: ResponsesResponseDto? = null
)

@Serializable
data class ImageGenerationRequestDto(
    val model: String,
    val prompt: String,
    val n: Int? = null,
    @SerialName("aspect_ratio")
    val aspectRatio: String? = null,
    val resolution: String? = null,
    @SerialName("response_format")
    val responseFormat: String = "b64_json"
)

@Serializable
data class ImageEditRequestDto(
    val model: String,
    val prompt: String,
    val image: ImageReferenceDto? = null,
    val images: List<ImageReferenceDto>? = null,
    @SerialName("aspect_ratio")
    val aspectRatio: String? = null,
    val resolution: String? = null,
    @SerialName("response_format")
    val responseFormat: String = "b64_json"
)

@Serializable
data class ImageReferenceDto(
    val type: String = "image_url",
    val url: String
)

@Serializable
data class ImageResponseDto(
    val data: List<ImageDataDto> = emptyList()
)

@Serializable
data class ImageDataDto(
    val url: String? = null,
    @SerialName("b64_json")
    val b64Json: String? = null
)
