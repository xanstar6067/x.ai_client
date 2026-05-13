package com.adam.xai_client.data.remote.dto

import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.data.remote.api.TokenUsage
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ApiRequestMessageDto>,
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
    val reasoningEffort: String? = null,
    @SerialName("search_parameters")
    val searchParameters: SearchParametersDto? = null,
    @SerialName("stream_options")
    val streamOptions: StreamOptionsDto? = null
)

@Serializable
data class ResponsesRequestDto(
    val model: String,
    val input: JsonElement,
    val stream: Boolean = false,
    @SerialName("prompt_cache_key")
    val promptCacheKey: String? = null,
    @SerialName("previous_response_id")
    val previousResponseId: String? = null,
    @SerialName("max_output_tokens")
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    val reasoning: ResponsesReasoningDto? = null,
    val tools: List<ResponsesToolDto>? = null,
    @SerialName("stream_options")
    val streamOptions: StreamOptionsDto? = null
)

@Serializable
data class SearchParametersDto(
    val mode: String = "auto",
    @SerialName("return_citations")
    val returnCitations: Boolean = true
)

@Serializable
data class ResponsesReasoningDto(
    val effort: String
)

@Serializable
data class ResponsesToolDto(
    val type: String
)

@Serializable
data class StreamOptionsDto(
    @SerialName("include_usage")
    val includeUsage: Boolean
)

@Serializable
data class ApiRequestMessageDto(
    val role: String,
    val content: JsonElement,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)

fun chatCompletionRequestDto(
    model: String,
    messages: List<ApiChatMessage>,
    stream: Boolean,
    settings: ChatModelSettings
): ChatCompletionRequestDto = ChatCompletionRequestDto(
    model = model,
    messages = messages.mapNotNull { it.toChatCompletionsMessage() },
    stream = stream,
    maxTokens = settings.maxTokens,
    temperature = settings.temperature,
    topP = settings.topP,
    frequencyPenalty = settings.frequencyPenalty,
    presencePenalty = settings.presencePenalty,
    reasoningEffort = settings.reasoningEffort?.apiName,
    searchParameters = SearchParametersDto().takeIf { settings.webSearchEnabled },
    streamOptions = StreamOptionsDto(includeUsage = true).takeIf { stream }
)

fun responsesRequestDto(
    model: String,
    messages: List<ApiChatMessage>,
    stream: Boolean,
    settings: ChatModelSettings,
    promptCacheKey: String? = null,
    previousResponseId: String? = null
): ResponsesRequestDto = ResponsesRequestDto(
    model = model,
    input = messages.toResponsesInput(model, previousResponseId),
    stream = stream,
    promptCacheKey = promptCacheKey,
    previousResponseId = previousResponseId,
    maxOutputTokens = settings.maxTokens,
    temperature = settings.temperature,
    topP = settings.topP,
    reasoning = settings.reasoningEffort?.let { ResponsesReasoningDto(it.apiName) },
    tools = listOf(ResponsesToolDto(type = "web_search")).takeIf { settings.webSearchEnabled },
    streamOptions = StreamOptionsDto(includeUsage = true).takeIf { stream }
)

private fun String.usesMultiAgentResponsesMessageFormat(): Boolean {
    return lowercase().startsWith("grok-4.20-multi-agent")
}

private fun List<ApiChatMessage>.toResponsesInput(
    model: String,
    previousResponseId: String?
): JsonElement {
    if (model.usesMultiAgentResponsesMessageFormat() && previousResponseId != null) {
        val followUpText = lastOrNull { it.role == "user" && it.attachments.isEmpty() }
            ?.content
            ?.takeIf { it.isNotBlank() }
        if (followUpText != null) {
            return JsonPrimitive(followUpText)
        }
    }
    val requestMessages = if (model.usesMultiAgentResponsesMessageFormat()) {
        mapNotNull { it.toMultiAgentResponsesMessage() }
    } else {
        mapNotNull { it.toResponsesMessage() }
    }
    return JsonArray(requestMessages.map { message ->
        buildJsonObject {
            put("role", message.role)
            put("content", message.content)
        }
    })
}

private fun ApiChatMessage.toChatCompletionsMessage(): ApiRequestMessageDto? {
    val imageAttachments = attachments.filter { it.kind == ApiMessageAttachmentKind.IMAGE }
    if (imageAttachments.isEmpty() && content.isBlank()) {
        return null
    }
    val blocks = buildList {
        if (content.isNotBlank()) {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", content)
                }
            )
        }
        imageAttachments.mapNotNull { it.dataUrl }.forEach { dataUrl ->
            add(
                buildJsonObject {
                    put("type", "image_url")
                    put(
                        "image_url",
                        buildJsonObject {
                            put("url", dataUrl)
                            put("detail", "auto")
                        }
                    )
                }
            )
        }
    }
    if (blocks.isEmpty()) {
        return null
    }
    if (imageAttachments.isEmpty()) {
        return ApiRequestMessageDto(
            role = role,
            content = JsonArray(blocks),
            reasoningContent = reasoningContent?.takeIf { it.isNotBlank() }
        )
    }
    return ApiRequestMessageDto(
        role = role,
        content = JsonArray(blocks),
        reasoningContent = reasoningContent?.takeIf { it.isNotBlank() }
    )
}

private fun ApiChatMessage.toResponsesMessage(): ApiRequestMessageDto? {
    val blocks = buildList {
        if (content.isNotBlank()) {
            add(
                buildJsonObject {
                    put("type", "input_text")
                    put("text", content)
                }
            )
        }
        attachments.forEach { attachment ->
            when (attachment.kind) {
                ApiMessageAttachmentKind.IMAGE -> attachment.dataUrl?.let { dataUrl ->
                    add(
                        buildJsonObject {
                            put("type", "input_image")
                            put("image_url", dataUrl)
                        }
                    )
                }
                ApiMessageAttachmentKind.DOCUMENT -> attachment.fileId?.let { fileId ->
                    add(
                        buildJsonObject {
                            put("type", "input_file")
                            put("file_id", fileId)
                        }
                    )
                }
            }
        }
    }
    if (blocks.isEmpty()) {
        return null
    }
    return ApiRequestMessageDto(role = role, content = JsonArray(blocks))
}

private fun ApiChatMessage.toMultiAgentResponsesMessage(): ApiRequestMessageDto? {
    if (role != "system" && role != "user" && role != "assistant") {
        return null
    }
    if (role == "assistant") {
        return content
            .takeIf { it.isNotBlank() }
            ?.let { ApiRequestMessageDto(role = role, content = JsonPrimitive(it)) }
    }
    if (attachments.isEmpty()) {
        return content
            .takeIf { it.isNotBlank() }
            ?.let { ApiRequestMessageDto(role = role, content = JsonPrimitive(it)) }
    }
    val blocks = buildList {
        if (content.isNotBlank()) {
            add(
                buildJsonObject {
                    put("type", "input_text")
                    put("text", content)
                }
            )
        }
        attachments.forEach { attachment ->
            when (attachment.kind) {
                ApiMessageAttachmentKind.IMAGE -> attachment.dataUrl?.let { dataUrl ->
                    add(
                        buildJsonObject {
                            put("type", "input_image")
                            put("image_url", dataUrl)
                        }
                    )
                }
                ApiMessageAttachmentKind.DOCUMENT -> attachment.fileId?.let { fileId ->
                    add(
                        buildJsonObject {
                            put("type", "input_file")
                            put("file_id", fileId)
                        }
                    )
                }
            }
        }
    }
    if (blocks.isEmpty()) {
        return null
    }
    return ApiRequestMessageDto(role = role, content = JsonArray(blocks))
}

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChatChoiceDto> = emptyList(),
    val usage: CompletionUsageDto? = null
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
data class CompletionUsageDto(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
    @SerialName("prompt_tokens_details")
    val promptTokensDetails: PromptTokensDetailsDto? = null,
    @SerialName("completion_tokens_details")
    val completionTokensDetails: CompletionTokensDetailsDto? = null
)

@Serializable
data class PromptTokensDetailsDto(
    @SerialName("cached_tokens")
    val cachedTokens: Int? = null,
    @SerialName("image_tokens")
    val imageTokens: Int? = null
)

@Serializable
data class CompletionTokensDetailsDto(
    @SerialName("reasoning_tokens")
    val reasoningTokens: Int? = null
)

@Serializable
data class ResponsesResponseDto(
    val id: String? = null,
    val output: List<ResponsesOutputDto> = emptyList(),
    @SerialName("output_text")
    val outputText: String? = null,
    val usage: ResponsesUsageDto? = null
)

@Serializable
data class ResponsesUsageDto(
    @SerialName("input_tokens")
    val inputTokens: Int? = null,
    @SerialName("output_tokens")
    val outputTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
    @SerialName("input_tokens_details")
    val inputTokensDetails: ResponsesInputTokensDetailsDto? = null,
    @SerialName("output_tokens_details")
    val outputTokensDetails: ResponsesOutputTokensDetailsDto? = null
)

@Serializable
data class ResponsesInputTokensDetailsDto(
    @SerialName("cached_tokens")
    val cachedTokens: Int? = null
)

@Serializable
data class ResponsesOutputTokensDetailsDto(
    @SerialName("reasoning_tokens")
    val reasoningTokens: Int? = null
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
    @SerialName("response_id")
    val responseId: String? = null,
    val response: ResponsesResponseDto? = null
)

@Serializable
data class UploadedFileDto(
    val id: String,
    val filename: String,
    val bytes: Long = 0
)

fun CompletionUsageDto.asDomain(): TokenUsage = TokenUsage(
    promptTokens = promptTokens ?: 0,
    completionTokens = completionTokens ?: 0,
    totalTokens = totalTokens ?: 0,
    cachedTokens = promptTokensDetails?.cachedTokens ?: 0,
    reasoningTokens = completionTokensDetails?.reasoningTokens ?: 0,
    imageTokens = promptTokensDetails?.imageTokens ?: 0
)

fun ResponsesUsageDto.asDomain(): TokenUsage = TokenUsage(
    promptTokens = inputTokens ?: 0,
    completionTokens = outputTokens ?: 0,
    totalTokens = totalTokens ?: 0,
    cachedTokens = inputTokensDetails?.cachedTokens ?: 0,
    reasoningTokens = outputTokensDetails?.reasoningTokens ?: 0
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

@Serializable
data class VideoGenerationRequestDto(
    val model: String,
    val prompt: String,
    val image: VideoReferenceDto? = null,
    val duration: Int? = null,
    @SerialName("aspect_ratio")
    val aspectRatio: String? = null,
    val resolution: String? = null
)

@Serializable
data class VideoEditRequestDto(
    val model: String,
    val prompt: String,
    val video: VideoReferenceDto,
    val duration: Int? = null,
    @SerialName("aspect_ratio")
    val aspectRatio: String? = null,
    val resolution: String? = null
)

@Serializable
data class VideoReferenceDto(
    val url: String? = null,
    @SerialName("file_id")
    val fileId: String? = null
)

@Serializable
data class VideoGenerationStartResponseDto(
    @SerialName("request_id")
    val requestId: String
)

@Serializable
data class VideoGenerationStatusResponseDto(
    val status: String,
    val progress: Int? = null,
    val video: VideoResultDto? = null,
    val model: String? = null,
    val error: VideoErrorDto? = null
)

@Serializable
data class VideoResultDto(
    val url: String? = null,
    val duration: Int? = null,
    @SerialName("respect_moderation")
    val respectModeration: Boolean? = null
)

@Serializable
data class VideoErrorDto(
    val code: String? = null,
    val message: String? = null
)
